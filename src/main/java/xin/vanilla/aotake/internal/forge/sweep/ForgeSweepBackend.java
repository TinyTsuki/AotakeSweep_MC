package xin.vanilla.aotake.internal.forge.sweep;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.entity.PartEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.api.sweep.*;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.*;
import xin.vanilla.aotake.data.world.ChunkVaultStorage;
import xin.vanilla.aotake.enums.EnumChunkCheckMode;
import xin.vanilla.aotake.internal.forge.rule.*;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.aotake.sweep.*;
import xin.vanilla.aotake.util.*;
import xin.vanilla.banira.common.config.ConfigReadSnapshot;
import xin.vanilla.banira.common.util.CollectionUtils;
import xin.vanilla.banira.common.util.EntityUtils;
import java.util.*;

public final class ForgeSweepBackend implements SweepBackend {
    private final MinecraftServer server;
    private final RuleManager<Entity> manager;
    private final RuleGeneration<Entity>.PolicyLease lease;
    private final RuleGeneration<Entity> generation;
    private final SweepRequest request;
    private final ForgeSweepTarget target;
    private final CommonConfig.BaseCategory settings;
    private final EntitySweeper sweeper;
    private final SweepResult result;
    private final ForgeSweepFilter filter;
    private final IdentityHashMap<Entity,NativeCandidate> candidates=new IdentityHashMap<>();
    private final IdentityHashMap<ServerWorld,String> dimensions=new IdentityHashMap<>();
    private ChunkSweepSelection<EntityAccess> chunkSelection;
    private EntitySweeper.RecyclingBatch recycling;
    private boolean touched;

    public ForgeSweepBackend(MinecraftServer server,RuleManager<Entity> manager,RuleGeneration<Entity>.PolicyLease lease,
                             SweepRequest request,ForgeSweepTarget target,EntitySweeper sweeper,SweepResult result) {
        this.server=server; this.manager=manager; this.lease=lease; this.request=request; this.target=target; this.sweeper=sweeper; this.result=result;
        generation=manager.acquire(RuleManager.RuleUse.SWEEP);
        if(generation.id()!=lease.generationId()) throw new IllegalStateException("Sweep generation changed during construction");
        settings=SweepConfiguration.base(lease.configuration());
        filter=new ForgeSweepFilter(settings,generation,request.trigger()==SweepTrigger.CHUNK);
        if(result.isChunkOverloadVault() && settings.chunk().chunkVaultEnabled()) {
            if(result.getChunkVaultTimePrefix()==null) result.setChunkVaultTimePrefix(ChunkVaultStorage.vaultTimePrefix());
            if(result.getChunkVaultRunId()==null) result.setChunkVaultRunId(ChunkVaultStorage.newVaultRunId());
        }
    }
    public CommonConfig.BaseCategory settings(){ return settings; }
    public SweepResult result(){ return result; }
    public boolean noOnlinePlayer(){ return server.getPlayerList().getPlayers().isEmpty() && !settings.sweep().sweepWhenNoPlayer(); }
    public long generationId(){ return lease.generationId(); }
    public void requireUsable(){
        if(AotakeSweep.isDisable() || !server.isRunning() || !manager.isUsable(generation.id())) throw new IllegalStateException("Sweep generation unavailable");
    }
    public ServerAccess server(Runnable callbackGuard,Runnable entityGuard){ return new ForgeServerAccess(()->server,callbackGuard,entityGuard); }
    public NativeMembers members(){ return lease.members(); }
    public ConfigReadSnapshot configuration(){ return lease.configuration(); }
    public boolean collectedCandidatesAreCanonical(){ return true; }
    public List<EntityAccess> collect(SweepRequest request,SweepScope scope,Runnable guard) {
        List<EntityAccess> entities=new ArrayList<>();
        if(request.operation()==SweepOperation.RECYCLE && request.trigger()!=SweepTrigger.CHUNK && noOnlinePlayer()) return entities;
        for(ServerWorld world:server.getAllLevels()) {
            if(scope.dimension()!=null && !scope.dimension().equals(dimensionId(world))) continue;
            Iterable<Entity> candidates = target.bounds() == null
                    ? (Iterable<Entity>) world.getEntities()::iterator
                    : world.getEntitiesOfClass(Entity.class, target.bounds());
            for(Entity entity:candidates) {
                NativeCandidate accepted=admit(entity,scope);
                if(accepted!=null) entities.add(accepted.view(guard));
            }
        }
        return entities;
    }
    public EntityAccess normalize(EntityAccess view,SweepScope scope,Runnable guard) {
        NativeCandidate candidate=admit(view.nativeEntity(Entity.class),scope);
        return candidate==null?null:candidate.view(guard);
    }
    private NativeCandidate admit(Entity entity,SweepScope scope) {
        if(entity instanceof PartEntity) entity=((PartEntity<?>)entity).getParent();
        if(!AotakeUtils.prepareSweepCandidate(entity)) return null;
        ServerWorld world=(ServerWorld)entity.level;
        if(world.getServer()!=server || world.getEntity(entity.getId())!=entity) return null;
        String dimension=dimensionId(world);
        if(!scope.contains(dimension,entity.blockPosition().getX()>>4,entity.blockPosition().getZ()>>4)
                || !target.contains(dimension,entity.getBoundingBox(),entity instanceof PlayerEntity,entity instanceof ItemEntity)) return null;
        NativeCandidate existing=candidates.get(entity);
        ChunkKey chunk=request.trigger()==SweepTrigger.CHUNK?ChunkKey.of(entity):null;
        if(existing!=null) return existing.world==world && Objects.equals(existing.chunk,chunk)?existing:null;
        NativeCandidate added=new NativeCandidate(entity,world,chunk);
        candidates.put(entity,added);
        return added;
    }
    private String dimensionId(ServerWorld world) {
        String id=dimensions.get(world);
        if(id==null) {
            id=world.dimension().location().toString();
            dimensions.put(world,id);
        }
        return id;
    }
    private static final class NativeCandidate {
        private final Entity entity;
        private final ServerWorld world;
        private final ChunkKey chunk;
        private EntityAccess view;
        NativeCandidate(Entity entity,ServerWorld world,ChunkKey chunk){ this.entity=entity; this.world=world; this.chunk=chunk; }
        EntityAccess view(Runnable guard){
            if(view==null) view=new ForgeEntityAccess(entity,guard);
            return view;
        }
    }
    public Object identity(EntityAccess entity){ return entity.nativeEntity(Entity.class); }
    public DefaultSweepFilter.Entry<EntityAccess,Object> evaluate(SweepRequest request,EntityAccess view) {
        return filter.inspect(view,view.nativeEntity(Entity.class));
    }
    public List<EntityAccess> filter(SweepRequest request,List<DefaultSweepFilter.Entry<EntityAccess,Object>> scans) {
        List<EntityAccess> selected=DefaultSweepFilter.selectEntries(scans,settings.sweep().entityListLimit(),settings.safe().safeBlocksEntityLimit());
        if(request.trigger()!=SweepTrigger.CHUNK) return selected;
        boolean advanced=settings.chunk().chunkCheckMode()!=EnumChunkCheckMode.DEFAULT;
        chunkSelection=ChunkSweepSelection.select(selected,view->{
            Entity entity=view.nativeEntity(Entity.class); ChunkKey key=ChunkKey.of(entity);
            return new ChunkKey(key.dimension(),key.chunkX(),key.chunkZ(),advanced?EntityUtils.getEntityRegistryString(entity):null);
        },settings.chunk().chunkCheckLimit(),settings.chunk().chunkCheckRetain());
        return chunkSelection.selected();
    }
    public Map<ChunkKey,List<Entity>> noticeGroups() {
        Map<ChunkKey,List<Entity>> groups=new LinkedHashMap<>();
        if(chunkSelection!=null) chunkSelection.groups().forEach((key,views)-> {
            List<Entity> entities=new ArrayList<>(); for(EntityAccess view:views) entities.add(view.nativeEntity(Entity.class)); groups.put(key,entities);
        });
        return groups;
    }
    public List<EntityAccess> preflight(SweepRequest request,SweepScope scope,List<EntityAccess> selected) {
        if(request.operation()==SweepOperation.RECYCLE) {
            requireUsable();
            boolean captureEnabled=!settings.entityCatch().catchItem().isEmpty();
            boolean itemRules=generation.hasRules(RuleGeneration.RuleList.REDLIST);
            boolean captureRules=captureEnabled && generation.hasRules(RuleGeneration.RuleList.CAPTURE);
            List<Entity> entities=new ArrayList<>();
            if(itemRules || captureRules) for(EntityAccess view:selected) {
                Entity entity=view.nativeEntity(Entity.class);
                if(entity instanceof ItemEntity ? !itemRules : !captureRules) continue;
                NativeCandidate candidate=admit(entity,scope);
                if(candidate!=null) entities.add(candidate.entity);
            }
            new CleanupRuleGate<>(generation,()->manager.isUsable(generation.id())).validate(entities,e->e instanceof ItemEntity,captureEnabled);
        }
        return selected;
    }
    public List<List<EntityAccess>> batches(SweepRequest request,List<EntityAccess> selected) {
        return CollectionUtils.splitToCollections(selected,settings.batch().sweepEntityLimit(),settings.batch().sweepBatchLimit());
    }
    public void execute(BatchContext batch,Progress progress) {
        requireUsable(); touched=true; result.setTotalBatch(batch.total());
        List<Entity> entities=new ArrayList<>();
        for(EntityAccess view:batch.entities()) {
            NativeCandidate candidate=admit(view.nativeEntity(Entity.class),batch.task().scope());
            if(candidate==null) progress.skipped(1);
            else entities.add(candidate.entity);
        }
        if(request.operation()==SweepOperation.RECYCLE) {
            if(recycling==null) {
                SweepScope scope=batch.task().scope();
                recycling=sweeper.beginRecycling(result,generation,settings,entity->admit(entity,scope)!=null);
            }
            recycling.execute(entities,progress);
        } else if(request.operation()==SweepOperation.DELETE) {
            for(Entity entity:entities) {
                requireUsable();
                if(!EntitySweeper.tryScheduleRemoveEntity(entity,false)){ progress.skipped(1); continue; }
                progress.processed(1,0,1,false);
                if(entity instanceof ItemEntity) result.plusItemCount(((ItemEntity)entity).getItem().getCount());
                else result.plusEntityCount();
            }
        }
        result.incrementBatch();
    }
    public void flush(){ if(touched && request.operation()==SweepOperation.RECYCLE) EntitySweeper.flushPendingStorage(); }
    public void release(){ filter.clear(); candidates.clear(); dimensions.clear(); chunkSelection=null; recycling=null; }
    public void fault(Throwable failure){ manager.failGeneration(generation.id(),failure); }
}
