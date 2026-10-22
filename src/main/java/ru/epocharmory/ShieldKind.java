package ru.epocharmory;

public enum ShieldKind {
    BUCKLER("buckler","Баклер",.72f,.45f,5,7,0.65f,0.0f),
    ROUND("round","Круглый щит",.88f,.88f,8,5,1.00f,.015f),
    KITE("kite","Каплевидный щит",.90f,.97f,10,3,1.18f,.030f),
    HEATER("heater","Треугольный щит",.92f,.90f,8,5,1.03f,.020f),
    SCUTUM("scutum","Скутум",.97f,.99f,13,2,1.45f,.060f),
    ASPIS("aspis","Аспис",.96f,.94f,12,3,1.36f,.050f),
    PAVISE("pavise","Павеза",.90f,1.0f,14,1,1.60f,.075f),
    KALKAN("kalkan","Калкан",.85f,.94f,7,5,.82f,.010f),
    TARGE("targe","Венгерский тарч",.92f,.95f,9,4,1.08f,.025f),
    ROTELLA("rotella","Ротелла",.96f,.90f,10,4,1.30f,.040f);
    public final String key,russian;
    public final float melee,projectile,durability,slowdown;
    public final int stamina,parryTicks;
    ShieldKind(String key,String ru,float melee,float proj,int stamina,int parry,float durability,float slow) {
        this.key=key;russian=ru;this.melee=melee;projectile=proj;this.stamina=stamina;
        parryTicks=parry;this.durability=durability;slowdown=slow;
    }
}
