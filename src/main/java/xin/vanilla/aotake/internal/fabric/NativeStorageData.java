package xin.vanilla.aotake.internal.fabric;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;

public final class NativeStorageData {
    private NativeStorageData() { }

    public static Tag item(ItemStack stack, HolderLookup.Provider registries) {
        return stack.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(
                registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static ItemStack item(Tag tag, HolderLookup.Provider registries) {
        if (tag == null || tag instanceof CompoundTag compound && compound.isEmpty()) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
    }

    public static CompoundTag entity(Entity entity) {
        var output = entityOutput(entity);
        if (!entity.save(output)) throw new IllegalStateException("Entity cannot be saved: " + entity.getUUID());
        return output.buildResult();
    }

    public static CompoundTag entityWithoutId(Entity entity) {
        var output = entityOutput(entity);
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static TagValueOutput entityOutput(Entity entity) {
        return TagValueOutput.createWithContext(new ProblemReporter() {
            @Override public ProblemReporter forChild(PathElement path) { return this; }
            @Override public void report(Problem problem) {
                throw new IllegalStateException("Cannot serialize entity " + entity.getUUID() + ": " + problem);
            }
        }, entity.registryAccess());
    }

    public static ListTag inventory(SimpleContainer inventory, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (ItemStack stack : inventory.getItems()) if (!stack.isEmpty()) result.add(item(stack, registries));
        return result;
    }

    public static void inventory(SimpleContainer inventory, ListTag list, HolderLookup.Provider registries) {
        inventory.clearContent();
        for (Tag tag : list) inventory.addItem(item(tag, registries));
    }
}
