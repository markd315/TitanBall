package networking;

import gameserver.engine.GameEngine;

import java.util.HashMap;
import java.util.Map;

public class GameStateDiff {
    public GameEngine fullState;  // Used if the entire state needs to be sent
    public Map<String, Object> changedFields = new HashMap<>();
}