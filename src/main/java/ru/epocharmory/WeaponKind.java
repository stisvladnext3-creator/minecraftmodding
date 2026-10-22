package ru.epocharmory;

public enum WeaponKind {
    ARMING_SWORD("arming_sword", "Одноручный меч", 7, 1.8f, 3.1f, 0.05f, false, "slash", "long_blade", 1),
    LONGSWORD("longsword", "Двуручный меч", 9, 1.25f, 3.6f, .10f, true, "slash", "long_blade", 2),
    MACE("mace", "Булава", 7, 1.45f, 2.8f, .30f, false, "blunt", "head", 1),
    SPEAR("spear", "Копьё", 6, 1.55f, 4.4f, .15f, false, "pierce", "short_blade", 1),
    KATANA("katana", "Катана", 7, 2.0f, 3.3f, .03f, true, "slash", "long_blade", 1),
    DAGGER("dagger", "Кинжал", 4, 2.8f, 2.3f, .10f, false, "pierce", "short_blade", 1),
    MORNINGSTAR("morningstar", "Моргенштерн", 8, 1.25f, 3.0f, .25f, false, "blunt", "head", 2),
    BATTLEAXE("battleaxe", "Боевой топор", 9, 1.05f, 3.2f, .15f, true, "slash", "head", 2),
    HALBERD("halberd", "Алебарда", 9, 1.0f, 4.5f, .25f, true, "pierce", "head", 2),
    WAR_HAMMER("war_hammer", "Боевой молот", 9, 1.0f, 2.9f, .40f, false, "blunt", "head", 2),
    RAPIER("rapier", "Рапира", 5.5f, 2.3f, 3.6f, .20f, false, "pierce", "long_blade", 1),
    SABRE("sabre", "Сабля", 6.5f, 2f, 3.2f, .04f, false, "slash", "long_blade", 1),
    GLADIUS("gladius", "Гладиус", 6, 2.1f, 2.7f, .10f, false, "pierce", "short_blade", 2),
    KUKRI("kukri", "Кукри", 5.5f, 2.2f, 2.5f, .05f, false, "slash", "short_blade", 1),
    KATAR("katar", "Катар", 5, 2.5f, 2.4f, .25f, false, "pierce", "short_blade", 1),
    NAGINATA("naginata", "Нагината", 8, 1.3f, 4.3f, .10f, true, "slash", "long_blade", 1),
    PIKE("pike", "Пика", 6.5f, .9f, 5.5f, .20f, true, "pierce", "short_blade", 1),
    FLAIL("flail", "Цепной кистень", 8.5f, 1.1f, 3.4f, .20f, false, "blunt", "head", 2);
    public final String key, russian, damageType, part;
    public final float damage, speed, reach, penetration;
    public final boolean twoHanded;
    public final int cost;
    WeaponKind(String key,String ru,float dmg,float speed,float reach,float pen,boolean two,String type,String part,int cost) {
        this.key=key; russian=ru; damage=dmg; this.speed=speed; this.reach=reach; penetration=pen;
        twoHanded=two; damageType=type; this.part=part; this.cost=cost;
    }
}
