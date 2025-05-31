package xin.vanilla.aotake.data;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

@Setter
@Getter
@Accessors(chain = true)
public class JunkBall extends Item {
    private Entity entity;

    public JunkBall() {
        super(new Item.Properties()
                // .tab(new ItemGroup("aotake_sweep") {
                //     @NonNull
                //     @Override
                //     public ItemStack makeIcon() {
                //         return new ItemStack(AotakeSweep.JUNK_BALL);
                //     }
                // })
        );
    }

    public JunkBall(Entity entity) {
        this();
        this.entity = entity;
    }

    public JunkBall(Properties properties) {
        super(properties);
    }

    @NonNull
    @Override
    public ActionResult<ItemStack> use(World level, @NonNull PlayerEntity player, @NonNull Hand hand) {
        if (!level.isClientSide) {

        }
        return ActionResult.success(player.getItemInHand(hand));
    }

    @NonNull
    @Override
    public ActionResultType useOn(ItemUseContext context) {
        if (!context.getLevel().isClientSide()) {

        }
        return super.useOn(context);
    }

}
