package networking;

import client.graphical.GoalSprite;
import client.graphical.ScreenConst;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.luben.zstd.Zstd;
import gameserver.Const;
import gameserver.TutorialOverrides;
import gameserver.effects.EffectId;
import gameserver.effects.EffectPool;
import gameserver.effects.cooldowns.CooldownCurve;
import gameserver.effects.cooldowns.CooldownQ;
import gameserver.effects.cooldowns.CooldownSteal;
import gameserver.effects.cooldowns.CooldownW;
import gameserver.effects.effects.*;
import gameserver.engine.*;
import gameserver.entity.*;
import gameserver.entity.minions.*;
import gameserver.gamemanager.GamePhase;
import gameserver.models.Game;
import gameserver.targeting.*;
import gameserver.targeting.core.Filter;
import gameserver.targeting.core.Limiter;
import gameserver.targeting.core.Selector;
import org.joda.time.Instant;
import util.ConstOperations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class KryoRegistry {
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        register(kryo);
        return kryo;
    });

    public static Object deserializeWithKryo(String base64String) {
        try {
            byte[] data = Base64.getDecoder().decode(base64String);

            //System.out.println("Decoded message size: " + data.length + " bytes");
            if (data.length == 0) {
                System.err.println("Failed to deserialize WebSocket message: Decoded data buffer is empty");
                return null;
            }

            int bufferSize = Math.max(data.length, 8 * 1024 * 1024);
            Input input = new Input(new ByteArrayInputStream(Zstd.decompress(data, data.length * 10)), bufferSize);

            return kryoThreadLocal.get().readClassAndObject(input);
        } catch (Exception e) {
            System.err.println("Failed to deserialize WebSocket message: " + e.getMessage());
            return null;
        }
    }

    public static String serializeWithKryo(Object object) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos, 64 * 1024)) { // Ensure proper buffer size
            kryoThreadLocal.get().writeClassAndObject(output, object);
            output.flush(); // Ensure data is fully written
            return Base64.getEncoder().encodeToString(Zstd.compress(baos.toByteArray()));
        } catch (Exception e) {
            System.err.println("Failed to serialize WebSocket message: " + e.getMessage());
            return "";
        }
    }

    public static void register(Kryo in){
        UUIDSerializer uSer = new UUIDSerializer();
        AtomicBooleanSerializer aSer = new AtomicBooleanSerializer();

        in.register(EffectId.class);
        in.register(Effect.class);
        in.register(TestEffect.class);
        in.register(FlareEffect.class);
        in.register(BleedEffect.class);
        in.register(BombEffect.class);
        in.register(EmptyEffect.class);
        in.register(RatioEffect.class);
        in.register(HealEffect.class);
        in.register(DeadEffect.class);
        in.register(DefenseEffect.class);
        in.register(ShootEffect.class);
        in.register(HideBallEffect.class);
        in.register(CooldownQ.class);
        in.register(CooldownW.class);
        in.register(CooldownSteal.class);
        in.register(CooldownCurve.class);

        in.register(Collidable.class);
        in.register(Box.class);
        in.register(Trap.class);
        in.register(Wall.class);
        in.register(Portal.class);
        in.register(BallPortal.class);
        in.register(Cage.class);
        in.register(Wolf.class);
        in.register(Fire.class);

        in.register(GameEngine.class, 95);
        in.register(Game.class);
        in.register(GoalHoop.class);
        in.register(GoalHoop[].class);
        in.register(ClientPacket.class);
        in.register(ClientPacket[].class);
        in.register(Titan.class);
        in.register(Titan.TitanState.class);
        in.register(Titan[].class);
        in.register(TitanType.class);
        in.register(TeamAffiliation.class);
        in.register(Entity.class);
        in.register(Entity[].class);
        in.register(Coordinates.class);
        in.register(GoalSprite.class);
        in.register(ShapePayload.class);
        in.register(PlayerDivider.class);
        in.register(Masteries.class);
        in.register(Team.class);
        in.register(ShapePayload.class);
        in.register(ShapePayload.ShapeSelector.class);
        in.register(ClientPacket.ARTISAN_SHOT.class);
        in.register(ClientPacket.class);
        in.register(EffectPool.class);
        in.register(Selector.class);
        in.register(SelectorOffset.class);
        in.register(SortBy.class);
        in.register(ShapePayload.class);
        in.register(Filter.class);
        in.register(Limiter.class);
        in.register(Targeting.class);
        in.register(DistanceFilter.class);
        in.register(Ability.class);
        in.register(AbilityStrategy.class);
        in.register(RangeCircle.class);
        in.register(StatEngine.class);

        in.register(UUID.class, uSer);
        in.register(AtomicBoolean.class, aSer);
        in.register(Instant.class);
        in.register(Integer.class);
        in.register(Double.class);
        in.register(Float.class);
        in.register(Long.class);
        in.register(java.util.ArrayList.class);
        in.register(java.util.List.class);
        in.register(Map.class);
        in.register(HashMap.class);
        in.register(String.class);
        in.register(java.util.HashSet.class);
        in.register(ScreenConst.class);

        in.register(byte.class);
        in.register(byte[].class);
        in.register(int[].class);
        in.register(int.class);
        in.register(float[].class);
        in.register(float.class);
        in.register(double[].class);
        in.register(double.class);
        in.register(boolean[].class);
        in.register(boolean.class);
        in.register(Const.class);
        in.register(ConstOperations.class);
        in.register(GamePhase.class);

        in.register(TutorialOverrides.class);
        in.register(GameOptions.class);
        //Log.DEBUG();
        //Log.TRACE();
    }
}
