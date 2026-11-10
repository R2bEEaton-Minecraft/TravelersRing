package cc.spea.travelersring.ring;

/** Where the ring being used lives. CURIOS = best ring equipped in a Curios slot (keybind). */
public enum RingSource {
    CURIOS,
    MAIN_HAND,
    OFF_HAND;

    public static RingSource byOrdinal(int i) {
        RingSource[] v = values();
        return i >= 0 && i < v.length ? v[i] : CURIOS;
    }
}
