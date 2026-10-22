package ru.epocharmory;

public enum ArmorStyle {
    MAIL("mail","Кольчуга",new int[]{2,5,4,1},.020f),
    LAMELLAR("lamellar","Ламелляр",new int[]{2,6,5,2},.025f),
    BRIGANDINE("brigandine","Бригантина",new int[]{2,7,5,2},.020f),
    PLATE("plate","Латы",new int[]{3,7,6,2},.045f);
    public final String key,russian;
    public final int[] armor;
    public final float slowdown;
    ArmorStyle(String key,String ru,int[] armor,float slow) {this.key=key;russian=ru;this.armor=armor;slowdown=slow;}
}
