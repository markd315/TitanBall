package networking;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicBooleanSerializer extends Serializer<AtomicBoolean> {
    @Override
    public void write(final Kryo kryo, final Output output, final AtomicBoolean object)
    {
        output.writeBoolean(object.get());
    }

    @Override
    public AtomicBoolean read(final Kryo kryo, final Input input, final Class<AtomicBoolean> type)
    {
        return new AtomicBoolean(input.readBoolean());
    }
}
