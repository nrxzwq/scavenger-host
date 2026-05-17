package meow.binary.scavenger.client;

import dev.architectury.registry.registries.RegistrySupplier;
import meow.binary.scavenger.data.RunMode;
import meow.binary.scavenger.data.modifier.ScavengerModifier;
import meow.binary.scavenger.registry.Modifiers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ClientScavengerData {
    public static Item item = Items.AIR;
    public static Identifier modifier = Modifiers.NONE.getId();
    public static long startTimestamp = 0L;
    public static long winTimestamp = 0L;
    public static RunMode mode = RunMode.SOLO;
    public static String winnerName = "";

    public static void clear() {
        item = Items.AIR;
        modifier = Modifiers.NONE.getId();
        startTimestamp = 0L;
        winTimestamp = 0L;
        mode = RunMode.SOLO;
        winnerName = "";
    }

    public static boolean isEmpty() {
        return startTimestamp <= 0L || (modifier.equals(Modifiers.NONE.getId()) && item.equals(Items.AIR));
    }

    public static boolean is(RegistrySupplier<ScavengerModifier> otherModifier) {
        return modifier.equals(otherModifier.getId());
    }
}
