package buscript;

import org.mozilla.javascript.ScriptableObject;

class BuscriptScope extends ScriptableObject {

    @Override
    public String getClassName() {
        return "Buscript";
    }
}
