package meow.binary.scavenger.network;

import it.hurts.shatterbyte.shatterlib.module.network.Packet;
import meow.binary.scavenger.Scavenger;
import meow.binary.scavenger.data.RunMode;
import meow.binary.scavenger.registry.Modifiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public class SyncScavengerDataPacket extends Packet {
    public static Type<SyncScavengerDataPacket> TYPE = Packet.createType(Scavenger.MOD_ID, "sync_scavenger_data");
    public static StreamCodec<RegistryFriendlyByteBuf, SyncScavengerDataPacket> STREAM_CODEC = Packet.createCodec(SyncScavengerDataPacket::write, SyncScavengerDataPacket::new);

    public Item getItem() {
        return item;
    }
    public Identifier getModifier() {
        return modifier;
    }
    public long getStartTimestamp() {
        return startTimestamp;
    }
    public long getWinTimestamp() {
        return winTimestamp;
    }
    public RunMode getMode() {
        return mode;
    }
    public String getWinnerName() {
        return winnerName;
    }

    Item item;
    Identifier modifier;
    long startTimestamp;
    long winTimestamp;
    RunMode mode;
    String winnerName;
    public boolean isWin;

    public SyncScavengerDataPacket(RegistryFriendlyByteBuf buf) {
        super(buf);
        this.item = BuiltInRegistries.ITEM.getValue(buf.readIdentifier());
        this.modifier = buf.readIdentifier();
        this.startTimestamp = buf.readLong();
        this.winTimestamp = buf.readLong();
        this.mode = RunMode.byId(buf.readUtf(32));
        this.winnerName = buf.readUtf(128);
        this.isWin = buf.readBoolean();
    }

    public SyncScavengerDataPacket(Item item, Identifier modifier, long startTimestamp, long winTimestamp, RunMode mode, String winnerName, boolean isWin) {
        this.item = item;
        this.modifier = modifier;
        this.startTimestamp = startTimestamp;
        this.winTimestamp = winTimestamp;
        this.mode = mode == null ? RunMode.SOLO : mode;
        this.winnerName = winnerName == null ? "" : winnerName;
        this.isWin = isWin;
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(item.arch$registryName());
        buf.writeIdentifier(modifier);
        buf.writeLong(startTimestamp);
        buf.writeLong(winTimestamp);
        buf.writeUtf(mode.getId());
        buf.writeUtf(winnerName);
        buf.writeBoolean(isWin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
