package xin.vanilla.aotake;

import lombok.NonNull;
import xin.vanilla.banira.common.data.AbstractComponent;


public final class AotakeComponent extends AbstractComponent {

    public static final AotakeComponent INSTANCE = new AotakeComponent();

    private AotakeComponent() {
    }

    @Override
    protected @NonNull String modId() {
        return Identifier.id().modId();
    }

    public static AotakeComponent get() {
        return INSTANCE;
    }
}
