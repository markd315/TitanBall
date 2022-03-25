package gameserver.effects.effects;

import client.graphical.StaticImage;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import gameserver.effects.EffectId;
import gameserver.engine.GameEngine;
import gameserver.entity.Entity;
import org.joda.time.Duration;
import org.joda.time.Instant;

import java.awt.*;
import java.awt.image.RescaleOp;
import java.io.Serializable;
import java.time.LocalDateTime;

public abstract class Effect implements Serializable {

    public Entity on;
    public StaticImage icon;

    public abstract void onActivate(GameEngine context);
    public abstract void onCease(GameEngine context);
    public abstract void onTick(GameEngine context); //Writes data, call once per game tick

    public Image getIcon(){
        icon = new StaticImage();
        icon.loadImage("res/Effects/"+ this.getEffect().toString() +".png", 32, 32);
        return icon.getImage();
    }

    public RescaleOp getIconTrans(){
        icon = new StaticImage();
        icon.loadImage("res/Effects/"+ this.getEffect().toString() +".png", 32, 32);
        float[] scales = { 1f, 1f, 1f, 0.1f };
        float[] offsets = new float[4];
        RescaleOp rop = new RescaleOp(scales, offsets, null);
        return rop;
    }

    public Image getIconBig(){
        icon = new StaticImage();
        icon.loadImage("res/Effects/"+ this.getEffect().toString() +".png", 64, 64);
        return icon.getImage();
    }

    public Image getIconSmall(){
        icon = new StaticImage();
        icon.loadImage("res/Effects/"+ this.getEffect().toString() +".png", 16, 16);
        return icon.getImage();
    }

    public boolean tick(GameEngine context){//Removes from effect pool when expired
        if(LocalDateTime.now().isBefore(getBegin())){
            return false;
        }
        else if(LocalDateTime.now().isAfter(getEnd()) && active == true){
            onCease(context);
            percentLeft = 0.0;
            active = false;
            return false;
        }
        else{
            if(!active && !everActive){
                onActivate(context);
                everActive = true;
                active = true;
            }
            if(active){
                updatePercent();
                onTick(context);
            }
            return true;
        }
    }

    public static long subtract(LocalDateTime in1, LocalDateTime in2) {
        Instant start = new Instant(in1);
        Instant end = new Instant(in2);
        return new Duration(end,start).getMillis();
    }

    private void updatePercent() {
        this.percentLeft = 100.0 - (100.0*
                (subtract(LocalDateTime.now(), begin)) / (subtract(end, begin))
        );
    }

    public boolean check(){ //Read-only action, perform whenever
        if(LocalDateTime.now().isAfter(getEnd())){
            return false;
        }
        return true;
    }

    public void cull(GameEngine context){
        onCease(context);
        this.setEnd(LocalDateTime.now());
        this.everActive = true;
        this.active = false;
    }

    public Effect(EffectId effect, Entity on, int durationMillis){
        this(effect, on, durationMillis, 0);
    }

    public Effect(EffectId effect, Entity on, int durationMillis, int delayMillis){
        this.on = on;
        this.effect = effect;
        this.duration = durationMillis;
        this.delay = delayMillis;
        this.active = false;
        this.everActive = false;
        this.percentLeft = 100.0;
        LocalDateTime now = LocalDateTime.now();
        begin = now.plusNanos(1000000L * delayMillis);
        end = begin.plusNanos(1000000L * durationMillis);
    }

    @JsonProperty
    public EffectId effect;

    @JsonProperty
    public boolean ceased = false;

    @JsonProperty
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    public LocalDateTime begin, end;

    @JsonProperty
    public int duration, delay;

    @JsonProperty
    public boolean active, everActive;

    @JsonProperty
    double percentLeft;

    public double getPercentLeft(){
        return percentLeft;
    }

    public EffectId getEffect() {
        return effect;
    }

    public void setEffect(EffectId effect) {
        this.effect = effect;
    }

    public LocalDateTime getBegin() {
        return begin;
    }

    public void setBegin(LocalDateTime begin) {
        this.begin = begin;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public void setEnd(LocalDateTime end) {
        this.end = end;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public Effect(){}

    @Override
    public String toString() {
        return "Effect{" +
                "effect=" + effect +
                ", begin=" + begin +
                ", end=" + end +
                ", duration=" + duration +
                ", delay=" + delay +
                ", active=" + active +
                ", everActive=" + everActive +
                ", percentLeft=" + percentLeft +
                '}';
    }

    public Entity getOn() {
        return on;
    }
}
