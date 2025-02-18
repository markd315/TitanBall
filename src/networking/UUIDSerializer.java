package networking;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.UUID;


public class UUIDSerializer extends Serializer<UUID> {

    @Override
    public void write(final Kryo kryo, final Output output, final UUID object) {
        if (object == null) {
            output.writeLong(0L);
            output.writeLong(0L);
        }
        output.writeLong(object.getMostSignificantBits());
        output.writeLong(object.getLeastSignificantBits());
    }

    @Override
    public UUID read(final Kryo kryo, final Input input, final Class<UUID> type) {
        long firstLong = input.readLong();
        long secondLong = input.readLong();
        if (firstLong == 0L && secondLong == 0L) {
            return null;
        }
        return new UUID(firstLong, secondLong);
    }
}