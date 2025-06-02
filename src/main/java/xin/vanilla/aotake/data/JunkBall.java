package xin.vanilla.aotake.data;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

@Setter
@Getter
@Accessors(chain = true)
public class JunkBall extends Item {
    private Entity entity;

    public JunkBall() {
        super(new Item.Properties()
                // .tab(new CreativeModeTab("aotake_sweep") {
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
    public InteractionResultHolder<ItemStack> use(Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        if (!level.isClientSide) {

        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @NonNull
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {

        }
        return super.useOn(context);
    }

}
