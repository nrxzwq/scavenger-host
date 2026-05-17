package meow.binary.scavenger.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.Random;

public interface ScavengerWheelHost {
    Screen scavenger$getScreen();

    Random scavenger$getRandom();

    Item scavenger$getChosenItem();

    Identifier scavenger$getChosenModifier();

    void scavenger$setChosenItem(Item item);

    void scavenger$setChosenModifier(Identifier modifier);

    void scavenger$setStartButtonActive(boolean active);

    default boolean scavenger$includeWorldGenerationModifiers() {
        return false;
    }
}
