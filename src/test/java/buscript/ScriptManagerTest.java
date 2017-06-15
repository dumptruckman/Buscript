package buscript;

import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class ScriptManagerTest {

    private ScriptManager sm;

    @Before
    public void setUp() throws Exception {
        sm = new ScriptManager(Files.createTempDirectory("scripts").toFile(),
                Logger.getLogger("ScriptManagerTest"));
    }

    @Test
    public void testGlobals() {
        List testList = new ArrayList<>();
        sm.setScriptVariable("testList", testList);

        sm.runScript("testList.add(1);", null);
        assertEquals(1, testList.get(0));
        assertEquals(1, ((List) sm.getScriptVariable("testList")).get(0));

        testList.add(2);
        assertEquals(2, testList.get(1));
        assertEquals(2, ((List) sm.runScript("testList", null)).get(1));
    }

    @Test
    public void testGlobalMethods() {
        sm.addScriptMethods(new TestMethods());
        assertEquals(5, sm.runScript("add(2, 3)", null));
    }

    public static class TestMethods {
        public int add(int a, int b) {
            return a + b;
        }
    }
}