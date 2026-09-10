package gg.lightpractice.api;

import gg.lightpractice.kit.Kit;

import java.util.Collection;
import java.util.List;

/** Kit registry and persistence. */
public interface KitService {

    Kit get(String id);

    Collection<Kit> kits();

    List<String> names();

    List<Kit> enabled();

    /** Kits that may be queued, optionally filtered to ranked play. */
    List<Kit> queueable(boolean ranked);

    List<Kit> duellable();

    List<Kit> editable();

    /** Creates a kit from the current held inventory of the creator; null when the id is taken. */
    Kit create(String id);

    boolean delete(String id);

    boolean save(Kit kit);

    void saveAll();

    boolean exists(String id);
}
