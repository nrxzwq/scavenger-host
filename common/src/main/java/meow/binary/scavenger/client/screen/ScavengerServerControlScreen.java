package meow.binary.scavenger.client.screen;

import it.hurts.shatterbyte.shatterlib.client.animation.Tween;
import it.hurts.shatterbyte.shatterlib.client.animation.easing.EaseType;
import it.hurts.shatterbyte.shatterlib.client.animation.easing.TransitionType;
import meow.binary.scavenger.Scavenger;
import meow.binary.scavenger.client.screen.widget.ItemWheel;
import meow.binary.scavenger.client.screen.widget.ModifierWheel;
import meow.binary.scavenger.data.RunMode;
import meow.binary.scavenger.registry.Modifiers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

public class ScavengerServerControlScreen extends Screen implements ScavengerWheelHost {
    private final Minecraft minecraft;
    private final Random random = new Random();

    private Item chosenItem = Items.AIR;
    private Identifier chosenModifier = Modifiers.NONE.getId();
    private RunMode mode = RunMode.SOLO;

    private Tween widgetTween = Tween.create();

    private ItemWheel itemWheel;
    private ModifierWheel modifierWheel;

    private Button nextWidget;
    private Button startWidget;
    private Button manualWidget;
    private CycleButton<RunMode> modeWidget;

    public ScavengerServerControlScreen() {
        super(Component.translatable("scavenger.server_control.title"));
        this.minecraft = Minecraft.getInstance();
        this.itemWheel = new ItemWheel(this.width / 2 - 105, this.height / 2 - 105, 210, 210, this);
        this.modifierWheel = new ModifierWheel(this.width / 2 - 100, this.height / 2 - 88, 200, 176, this);

        boolean skip = Scavenger.CONFIG.gameplay.skipModifierWheel;

        this.nextWidget = Button.builder(skip ? Component.translatable("scavenger.start_run") : Component.translatable("scavenger.next_widget"), button -> {
                    if (skip) {
                        this.chosenModifier = Modifiers.NONE.getId();
                        this.startRun();
                        return;
                    }

                    button.active = false;
                    widgetTween.kill();
                    widgetTween = Tween.create();
                    widgetTween.setTransitionType(TransitionType.CUBIC);
                    widgetTween.tweenMethod(itemWheel::setyOffset, 0f, this.height + 0f, 0.66).setEaseType(EaseType.EASE_IN);
                    widgetTween.tweenRunnable(() -> {
                        modifierWheel.refreshModifiers();
                        if (modifierWheel.getModifierCount() < 2) {
                            this.chosenModifier = modifierWheel.getSingleModifierOrNone();
                            this.startRun();
                            return;
                        }

                        this.removeWidget(itemWheel);
                        itemWheel = null;

                        modifierWheel.setyOffset(this.height / 2f + 72);
                        this.rebuildWidgets();
                    });
                    widgetTween.tweenMethod(modifierWheel::setyOffset, this.height / 2 + 72f, 0f, 0.66).setEaseType(EaseType.EASE_OUT);
                    widgetTween.start();
                })
                .size(128, 20)
                .build();

        this.startWidget = Button.builder(Component.translatable("scavenger.start_run"), button -> this.startRun())
                .size(128, 20)
                .build();

        this.manualWidget = Button.builder(Component.translatable("scavenger.manual_selection"), button ->
                        this.minecraft.setScreen(new ServerManualSelectionScreen(this))
                )
                .size(128, 20)
                .build();

        this.nextWidget.active = false;
        this.startWidget.active = false;
    }

    @Override
    protected void init() {
        if (itemWheel != null) {
            itemWheel.setPosition(this.width / 2 - 105, this.height / 2 - 105);
            this.addRenderableWidget(itemWheel);
            nextWidget.setPosition(this.width / 2 - 64 + Scavenger.CONFIG.misc.menuButtonsXOffset, this.height - 28);
            this.addRenderableWidget(nextWidget);
        } else {
            modifierWheel.setPosition(this.width / 2 - 100, this.height / 2 - 88);
            this.addRenderableWidget(modifierWheel);
            startWidget.setPosition(this.width / 2 - 64 + Scavenger.CONFIG.misc.menuButtonsXOffset, this.height - 28);
            this.addRenderableWidget(startWidget);
        }

        manualWidget.setPosition(this.width / 2 - 64 + Scavenger.CONFIG.misc.menuButtonsXOffset, this.height - 52);
        this.addRenderableWidget(manualWidget);

        modeWidget = this.addRenderableWidget(
                CycleButton.builder(RunMode::getName, this.mode)
                        .withValues(List.of(RunMode.values()))
                        .displayOnlyValue()
                        .create(this.width / 2 - 64 + Scavenger.CONFIG.misc.menuButtonsXOffset, 18, 128, 20, Component.translatable("scavenger.mode"), (button, value) -> this.mode = value)
        );
        modeWidget.setValue(this.mode);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xffffffff);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        boolean handled = super.mouseClicked(event, isDoubleClick);
        if (handled || event.button() != 0 || !Scavenger.CONFIG.wheels.clickAnywhereToSpin) {
            return handled;
        }

        if (itemWheel != null) {
            return itemWheel.trySpin();
        }

        if (modifierWheel != null) {
            return modifierWheel.trySpin();
        }

        return false;
    }

    public void startRun() {
        this.startRun(this.chosenItem, this.chosenModifier, this.mode);
    }

    public void startRun(Item item, Identifier modifier, RunMode mode) {
        if (item == null || item == Items.AIR || this.minecraft.player == null || this.minecraft.player.connection == null) {
            return;
        }

        this.chosenItem = item;
        this.chosenModifier = modifier == null ? Modifiers.NONE.getId() : modifier;
        this.mode = mode == null ? RunMode.SOLO : mode;

        if (this.shouldRecreateWorldForModifier(this.chosenModifier)) {
            ScavengerWorldCreateScreen.queueRestart(this.minecraft, this.chosenItem, this.chosenModifier);
            this.minecraft.setScreen(null);
            this.minecraft.disconnectWithSavingScreen();
            return;
        }

        this.minecraft.player.connection.sendCommand("scavenger start " + this.chosenItem.arch$registryName() + " " + this.chosenModifier + " " + this.mode.getId());
        this.minecraft.setScreen(null);
    }

    private boolean shouldRecreateWorldForModifier(Identifier modifier) {
        return this.isPureSingleplayerWorld() && Scavenger.isWorldGenerationModifier(modifier);
    }

    private boolean isPureSingleplayerWorld() {
        var server = this.minecraft.getSingleplayerServer();
        return server != null && !server.isPublished();
    }

    public Item getChosenItem() {
        return chosenItem;
    }

    public Identifier getChosenModifier() {
        return chosenModifier;
    }

    public RunMode getMode() {
        return mode;
    }

    public void setChosenItem(Item item) {
        this.chosenItem = item;
        this.nextWidget.active = !chosenItem.equals(Items.AIR);
    }

    public void setChosenModifier(Identifier modifier) {
        this.chosenModifier = modifier;
        this.startWidget.active = true;
    }

    public void setMode(RunMode mode) {
        this.mode = mode == null ? RunMode.SOLO : mode;
    }

    @Override
    public Screen scavenger$getScreen() {
        return this;
    }

    @Override
    public Random scavenger$getRandom() {
        return this.random;
    }

    @Override
    public Item scavenger$getChosenItem() {
        return this.getChosenItem();
    }

    @Override
    public Identifier scavenger$getChosenModifier() {
        return this.getChosenModifier();
    }

    @Override
    public void scavenger$setChosenItem(Item item) {
        this.setChosenItem(item);
    }

    @Override
    public void scavenger$setChosenModifier(Identifier modifier) {
        this.setChosenModifier(modifier);
    }

    @Override
    public void scavenger$setStartButtonActive(boolean active) {
        this.startWidget.active = active;
    }

    @Override
    public boolean scavenger$includeWorldGenerationModifiers() {
        return this.isPureSingleplayerWorld();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
