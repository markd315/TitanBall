package networking;

import client.graphical.GoalSprite;
import client.graphical.ScreenConst;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
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
    private static Kryo kryo;
    public static Object deserializeWithKryo(String base64String) {
        try {
            byte[] data = Base64.getDecoder().decode(base64String);

            if (data.length == 0) {
                System.err.println("Failed to deserialize WebSocket message: Decoded data buffer is empty");
                return null;
            }

            int bufferSize = Math.max(data.length, 4096);
            Input input = new Input(new ByteArrayInputStream(data), bufferSize);

            return kryo.readClassAndObject(input);
        } catch (Exception e) {
            System.err.println("Failed to deserialize WebSocket message: " + e.getMessage());
            return null;
        }
    }

    public static String serializeWithKryo(Object object) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Output output = new Output(baos, 4096)) { // Ensure proper buffer size
            kryo.writeClassAndObject(output, object);
            output.flush(); // Ensure data is fully written
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            System.err.println("Failed to serialize WebSocket message: " + e.getMessage());
            return "";
        }
    }

    public static void register(Kryo in){
        kryo = in;
        UUIDSerializer uSer = new UUIDSerializer();
        AtomicBooleanSerializer aSer = new AtomicBooleanSerializer();


        kryo.register(EffectId.class);
        kryo.register(Effect.class);
        kryo.register(TestEffect.class);
        kryo.register(FlareEffect.class);
        kryo.register(BleedEffect.class);
        kryo.register(BombEffect.class);
        kryo.register(EmptyEffect.class);
        kryo.register(RatioEffect.class);
        kryo.register(HealEffect.class);
        kryo.register(DeadEffect.class);
        kryo.register(DefenseEffect.class);
        kryo.register(ShootEffect.class);
        kryo.register(HideBallEffect.class);
        kryo.register(CooldownQ.class);
        kryo.register(CooldownW.class);
        kryo.register(CooldownSteal.class);
        kryo.register(CooldownCurve.class);

        kryo.register(Collidable.class);
        kryo.register(Box.class);
        kryo.register(Trap.class);
        kryo.register(Wall.class);
        kryo.register(Portal.class);
        kryo.register(BallPortal.class);
        kryo.register(Cage.class);
        kryo.register(Wolf.class);
        kryo.register(Fire.class);

        kryo.register(GameEngine.class, 95);
        kryo.register(Game.class);
        kryo.register(GoalHoop.class);
        kryo.register(GoalHoop[].class);
        kryo.register(ClientPacket.class);
        kryo.register(ClientPacket[].class);
        kryo.register(Titan.class);
        kryo.register(Titan.TitanState.class);
        kryo.register(Titan[].class);
        kryo.register(TitanType.class);
        kryo.register(TeamAffiliation.class);
        kryo.register(Entity.class);
        kryo.register(Entity[].class);
        kryo.register(Coordinates.class);
        kryo.register(GoalSprite.class);
        kryo.register(ShapePayload.class);
        kryo.register(PlayerDivider.class);
        kryo.register(Masteries.class);
        kryo.register(Team.class);
        kryo.register(ShapePayload.class);
        kryo.register(ShapePayload.ShapeSelector.class);
        kryo.register(ClientPacket.ARTISAN_SHOT.class);
        kryo.register(ClientPacket.class);
        kryo.register(EffectPool.class);
        kryo.register(Selector.class);
        kryo.register(SelectorOffset.class);
        kryo.register(SortBy.class);
        kryo.register(ShapePayload.class);
        kryo.register(Filter.class);
        kryo.register(Limiter.class);
        kryo.register(Targeting.class);
        kryo.register(DistanceFilter.class);
        kryo.register(Ability.class);
        kryo.register(AbilityStrategy.class);
        kryo.register(RangeCircle.class);
        kryo.register(StatEngine.class);

        kryo.register(UUID.class, uSer);
        kryo.register(AtomicBoolean.class, aSer);
        kryo.register(Instant.class);
        kryo.register(Integer.class);
        kryo.register(Double.class);
        kryo.register(Float.class);
        kryo.register(Long.class);
        kryo.register(java.util.ArrayList.class);
        kryo.register(java.util.List.class);
        kryo.register(Map.class);
        kryo.register(HashMap.class);
        kryo.register(String.class);
        kryo.register(java.util.HashSet.class);
        kryo.register(ScreenConst.class);

        kryo.register(byte.class);
        kryo.register(byte[].class);
        kryo.register(int[].class);
        kryo.register(int.class);
        kryo.register(float[].class);
        kryo.register(float.class);
        kryo.register(double[].class);
        kryo.register(double.class);
        kryo.register(boolean[].class);
        kryo.register(boolean.class);
        kryo.register(Const.class);
        kryo.register(ConstOperations.class);
        kryo.register(GamePhase.class);

        kryo.register(TutorialOverrides.class);
        kryo.register(GameOptions.class);
        //Log.DEBUG();
        //Log.TRACE();
    }
}
