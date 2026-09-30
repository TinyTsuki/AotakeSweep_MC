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
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
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
    private final IdentityHashMap<Entity,ServerWorld> worlds=new IdentityHashMap<>();
    private final IdentityHashMap<Entity,ChunkKey> chunks=new IdentityHashMap<>();
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
    public ServerAccess server(Runnable guard){ return new ForgeServerAccess(()->server,guard); }
    public NativeMembers members(){ return lease.members(); }
    public ConfigEditSnapshot configuration(){ return lease.configuration(); }
    public List<EntityAccess> collect(SweepRequest request,SweepScope scope,Runnable guard) {
        List<EntityAccess> entities=new ArrayList<>();
        if(request.operation()==SweepOperation.RECYCLE && request.trigger()!=SweepTrigger.CHUNK && noOnlinePlayer()) return entities;
        for(ServerWorld world:server.getAllLevels()) {
            if(scope.dimension()!=null && !scope.dimension().equals(world.dimension().location().toString())) continue;
            Iterable<Entity> candidates = target.bounds() == null
                    ? (Iterable<Entity>) world.getEntities()::iterator
                    : world.getEntitiesOfClass(Entity.class, target.bounds());
            for(Entity entity:candidates) {
                EntityAccess accepted=normalize(new ForgeEntityAccess(entity,guard),scope,guard);
                if(accepted!=null) entities.add(accepted);
            }
        }
        return entities;
    }
    public EntityAccess normalize(EntityAccess view,SweepScope scope,Runnable guard) {
        Entity entity=view.nativeEntity(Entity.class);
        if(entity instanceof PartEntity) entity=((PartEntity<?>)entity).getParent();
        if(!AotakeUtils.prepareSweepCandidate(entity)) return null;
        ServerWorld world=(ServerWorld)entity.level;
        if(world.getServer()!=server || world.getEntity(entity.getId())!=entity) return null;
        String dimension=world.dimension().location().toString();
        if(!scope.contains(dimension,entity.blockPosition().getX()>>4,entity.blockPosition().getZ()>>4)
                || !target.contains(dimension,entity.getBoundingBox(),entity instanceof PlayerEntity,entity instanceof ItemEntity)) return null;
        ServerWorld original=worlds.putIfAbsent(entity,world);
        if(original!=null && original!=world) return null;
        if(request.trigger()==SweepTrigger.CHUNK) {
            ChunkKey key=ChunkKey.of(entity), before=chunks.putIfAbsent(entity,key);
            if(before!=null && !before.equals(key)) return null;
        }
        return new ForgeEntityAccess(entity,guard);
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
            List<Entity> entities=new ArrayList<>();
            for(EntityAccess view:selected) if(normalize(view,scope,()->{})!=null) entities.add(view.nativeEntity(Entity.class));
            new CleanupRuleGate<>(generation,()->manager.isUsable(generation.id())).prepare(entities,e->e instanceof ItemEntity,!settings.entityCatch().catchItem().isEmpty());
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
            if(normalize(view,batch.task().scope(),()->{})==null) progress.skipped(1);
            else entities.add(view.nativeEntity(Entity.class));
        }
        if(request.operation()==SweepOperation.RECYCLE) {
            if(recycling==null) recycling=sweeper.beginRecycling(result,generation,settings);
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
    public void release(){ filter.clear(); worlds.clear(); chunks.clear(); chunkSelection=null; recycling=null; }
    public void fault(Throwable failure){ manager.failGeneration(generation.id(),failure); }
}
