package gg.lightpractice.database.serialization;

import gg.lightpractice.util.Debug;
import gg.lightpractice.util.DebugCategory;
import org.bson.Document;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Bukkit item stacks to BSON documents and back.
 *
 * <p>Bukkit's own {@code ConfigurationSerializable} format is reused so enchantments, display names,
 * lore, 1.8 potion damage values and skull owners all survive the round trip. Numeric types coming
 * back out of MongoDB are normalised to the types Bukkit expects, and a single broken item is
 * skipped instead of failing the whole profile load.</p>
 */
public final class ItemSerialization {

    private ItemSerialization() {
    }

    public static List<Document> toDocuments(ItemStack[] items) {
        List<Document> documents = new ArrayList<Document>();
        if (items == null) {
            return documents;
        }
        for (int slot = 0; slot < items.length; slot++) {
            Document document = toItemDocument(items[slot], slot);
            if (document != null) {
                documents.add(document);
            }
        }
        return documents;
    }

    public static Document toItemDocument(ItemStack item, int slot) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || item.getAmount() <= 0) {
            return null;
        }
        try {
            Map<String, Object> serialized = item.serialize();
            Document document = new Document();
            document.put("slot", slot);
            for (Map.Entry<String, Object> entry : serialized.entrySet()) {
                document.put(entry.getKey(), toBson(entry.getValue()));
            }
            return document;
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.DATABASE, "Could not serialise item {} in slot {}: {}",
                    item.getType(), slot, throwable.getMessage());
            return null;
        }
    }

    public static ItemStack[] toArray(List<Document> documents, int size) {
        ItemStack[] items = new ItemStack[Math.max(0, size)];
        if (documents == null) {
            return items;
        }
        for (Document document : documents) {
            int slot = document.getInteger("slot", -1);
            if (slot < 0 || slot >= items.length) {
                continue;
            }
            items[slot] = toItemStack(document);
        }
        return items;
    }

    public static List<ItemStack> toList(List<Document> documents) {
        List<ItemStack> items = new ArrayList<ItemStack>();
        if (documents == null) {
            return items;
        }
        for (Document document : documents) {
            ItemStack item = toItemStack(document);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    public static ItemStack toItemStack(Document document) {
        if (document == null) {
            return null;
        }
        Document copy = new Document();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            if ("slot".equals(entry.getKey())) {
                continue;
            }
            copy.put(entry.getKey(), fromBson(entry.getKey(), entry.getValue()));
        }
        try {
            return ItemStack.deserialize(toMap(copy));
        } catch (Throwable throwable) {
            Debug.log(DebugCategory.DATABASE, "Could not deserialise a stored item ({}): {}",
                    copy.get("type"), throwable.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Document document) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Document) {
                map.put(entry.getKey(), toMap((Document) value));
            } else if (value instanceof List) {
                map.put(entry.getKey(), toListValue((List<Object>) value));
            } else {
                map.put(entry.getKey(), value);
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toListValue(List<Object> source) {
        List<Object> result = new ArrayList<Object>(source.size());
        for (Object value : source) {
            if (value instanceof Document) {
                result.add(toMap((Document) value));
            } else if (value instanceof List) {
                result.add(toListValue((List<Object>) value));
            } else {
                result.add(value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Object toBson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Document document = new Document();
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
                document.put(String.valueOf(entry.getKey()), toBson(entry.getValue()));
            }
            return document;
        }
        if (value instanceof Iterable) {
            List<Object> list = new ArrayList<Object>();
            for (Object entry : (Iterable<Object>) value) {
                list.add(toBson(entry));
            }
            return list;
        }
        if (value instanceof Object[]) {
            List<Object> list = new ArrayList<Object>();
            for (Object entry : (Object[]) value) {
                list.add(toBson(entry));
            }
            return list;
        }
        if (value instanceof ItemStack) {
            return toBson(((ItemStack) value).serialize());
        }
        if (value instanceof Byte || value instanceof Short) {
            return ((Number) value).intValue();
        }
        if (value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return value;
    }

    /** Converts a stored value back, keeping the numeric types Bukkit's deserialiser expects. */
    public static Object fromBson(String key, Object value) {
        if (value instanceof Document) {
            Document document = new Document();
            for (Map.Entry<String, Object> entry : ((Document) value).entrySet()) {
                document.put(entry.getKey(), fromBson(entry.getKey(), entry.getValue()));
            }
            return document;
        }
        if (value instanceof Number) {
            Number number = (Number) value;
            if ("damage".equalsIgnoreCase(key) || "Damage".equals(key)) {
                return number.shortValue();
            }
            if (number instanceof Double || number instanceof Float) {
                return number.doubleValue();
            }
            return number.intValue();
        }
        return value;
    }
}
