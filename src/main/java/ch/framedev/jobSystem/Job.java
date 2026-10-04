package ch.framedev.jobSystem;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class Job implements ConfigurationSerializable {

    private int id;
    private String name;

    public Job(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Job(Map<String, Object> map) {
        Object idValue = map.get("id");
        this.id = idValue instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(idValue));
        this.name = (String) map.get("name");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        return map;
    }
}
