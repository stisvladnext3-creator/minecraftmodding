package ru.epocharmory;

public enum Station {
    BLOOMERY("bloomery","Сыродутная печь",true,false,1200),
    CRUCIBLE("crucible","Тигельная печь",true,false,1200),
    FORGE("forge","Кузнечный горн",true,false,950),
    ANVIL("forging_anvil","Ковочная наковальня",false,true,0),
    QUENCH("quench_tub","Закалочная ванна",false,false,0),
    TEMPER("tempering_furnace","Печь отпуска",true,false,350),
    WIRE("wire_bench","Волочильный стан",false,true,0),
    RIVET("riveting_bench","Клепальный верстак",false,true,0),
    GRIND("grindstone_bench","Точильный станок",false,true,0),
    ARMORER("armorer_bench","Верстак оружейника",false,true,0),
    ELECTROLYZER("electrolyzer","Алхимический электролизёр",true,false,950);
    public final String key,russian;
    public final boolean heated,manual;
    public final int defaultTarget;
    Station(String key,String ru,boolean heated,boolean manual,int target) {
        this.key=key;russian=ru;this.heated=heated;this.manual=manual;defaultTarget=target;
    }
    public static Station named(String key) {
        for (var s:values()) if(s.key.equals(key))return s;
        throw new IllegalArgumentException("Unknown station "+key);
    }
}
