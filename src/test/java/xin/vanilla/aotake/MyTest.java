package xin.vanilla.aotake;

import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.aotake.util.JsonUtils;

public class MyTest {

    @Test
    public void JsonTest() {
        JsonObject element = new JsonObject();
        JsonUtils.set(element, "a.b.c", 123);
        JsonUtils.set(element, "c.[1]", "test1");
        JsonUtils.set(element, "d", new String[]{"test2", "test3"});
        JsonUtils.set(element, "d.[2]", new String[]{"test2", "test3"});
        System.out.println(JsonUtils.PRETTY_GSON.toJson(element));
        System.out.println(JsonUtils.getString(element, "c.[0]"));
        System.out.println(JsonUtils.getString(element, "c.[1]"));
        System.out.println(JsonUtils.getString(element, "d"));
        System.out.println(JsonUtils.getString(element, "d.[2]"));
    }
}
