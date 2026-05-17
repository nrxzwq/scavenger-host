package meow.binary.scavenger.network;

import it.hurts.shatterbyte.shatterlib.module.network.Packet;
import meow.binary.scavenger.Scavenger;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class OpenScavengerControlPacket extends Packet {
    public static Type<OpenScavengerControlPacket> TYPE = Packet.createType(Scavenger.MOD_ID, "open_control");
    public static StreamCodec<RegistryFriendlyByteBuf, OpenScavengerControlPacket> STREAM_CODEC = Packet.createCodec(OpenScavengerControlPacket::write, OpenScavengerControlPacket::new);

    public OpenScavengerControlPacket() {
    }

    public OpenScavengerControlPacket(RegistryFriendlyByteBuf buf) {
        super(buf);
    }

    @Override
    public void write(RegistryFriendlyByteBuf buf) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
