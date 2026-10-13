package xin.vanilla.aotake.config;

import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.aotake.screen.ProgressRender;

import java.util.Arrays;

import static org.junit.Assert.*;

public class NeoForgeExperienceVisibilityTest {
    private ConfigBaselineFixture fixture;

    @Before
    public void bindConfig() throws Exception {
        fixture = new ConfigBaselineFixture(ClientConfig.class);
        fixture.bind(ClientConfig.class);
    }

    private boolean hidden(EnumProgressBarType selected, boolean leaf, boolean pole, boolean text) {
        fixture.holder.set("progressBar.progressBarDisplayNormal", Arrays.asList(selected));
        fixture.holder.set("progressBar.leaf.hideExperienceBarLeaf", leaf);
        fixture.holder.set("progressBar.pole.hideExperienceBarPole", pole);
        fixture.holder.set("progressBar.text.hideExperienceBarText", text);
        return ProgressRender.shouldHideVanillaExperienceBar(false);
    }

    @Test public void leafFlagControlsSelectedLeaf() {
        assertFalse(hidden(EnumProgressBarType.LEAF, false, false, false));
        assertTrue(hidden(EnumProgressBarType.LEAF, true, false, false));
    }
    @Test public void poleFlagControlsSelectedPole() {
        assertFalse(hidden(EnumProgressBarType.POLE, false, false, false));
        assertTrue(hidden(EnumProgressBarType.POLE, false, true, false));
    }
    @Test public void textFlagControlsSelectedText() {
        assertFalse(hidden(EnumProgressBarType.TEXT, false, false, false));
        assertTrue(hidden(EnumProgressBarType.TEXT, false, false, true));
    }
    @Test public void unselectedFlagsDoNotHideExperience() {
        assertFalse(hidden(EnumProgressBarType.LEAF, false, true, true));
        assertFalse(hidden(EnumProgressBarType.POLE, true, false, true));
        assertFalse(hidden(EnumProgressBarType.TEXT, true, true, false));
    }
    @Test public void holdAndNormalUseTheirConfiguredDisplayLists() throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(getClass().getResourceAsStream("/xin/vanilla/aotake/screen/ProgressRender.class")).accept(node, 0);
        java.util.Set<String> calls = new java.util.HashSet<>();
        node.methods.stream().filter(m -> m.name.equals("getDisplayList")).forEach(m -> {
            for (org.objectweb.asm.tree.AbstractInsnNode i : m.instructions) if (i instanceof MethodInsnNode)
                calls.add(((MethodInsnNode) i).name);
        });
        assertTrue(calls.contains("progressBarDisplayHold"));
        assertTrue(calls.contains("progressBarDisplayNormal"));
    }
}
