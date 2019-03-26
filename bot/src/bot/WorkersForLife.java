package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

public class WorkersForLife extends AbstractionLayerAI {    
    private UnitTypeTable utt;
    private UnitType workerType;
    private UnitType baseType;
    
    public WorkersForLife(UnitTypeTable utt) {
        super(new AStarPathFinding());
        this.utt = utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
    }
    

    @Override
    public void reset() {
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
    }

    
    @Override
    public AI clone() {
        return new WorkersForLife(utt);
    }
   
    
    @Override
    public PlayerAction getAction(int player, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        boolean isRush = false;
        
        if ((pgs.getWidth() * pgs.getHeight()) <= 144){
            isRush = true;
        }
        
        // Fill list of all our workers
        List<Unit> workers = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) {
            if (u.getType().canHarvest
                    && u.getPlayer() == player) {
                workers.add(u);
            }
        }
        // Behaviour of workers
        if(isRush){
            rushWorkersBehavior(workers, p, pgs, gs);
        } else {
            rushWorkersBehavior(workers, p, pgs, gs);
        }
        
        // Behaviour of bases:
        for (Unit u : pgs.getUnits()) {
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) {
                
                if(isRush){
                    baseBehavior(u, p, pgs);
                }else {
                    baseBehavior(u, p, pgs);
                }
            }
        }
                       
        return translateActions(player, gs);
    }
    
    // Basic base behaviour will keep building workers
    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {

        int nbases = 0;
        int nworkers = 0;

        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) {
                nworkers++;
            }
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                nbases++;
            }
        }
        if (p.getResources() >= workerType.cost) {
            train(u, workerType);
        }
    }
    
    public void rushWorkersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs) {
        int nbases = 0;
        int nworkers = 0;
        
        List<Unit> freeWorkers = new LinkedList<Unit>();
        List<Unit> battleWorkers = new LinkedList<Unit>();
        List<Unit> defenceWorkers = new LinkedList<Unit>();
        
        // Checks number of worker and bases the player has
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                nbases++;
            }
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) {
                nworkers++;
            }
        }
        // If no resources available sends all workers to battle
        //if (p.getResources() == 0){
        //    battleWorkers.addAll(workers);
        //} 
        // Applies a worker for each base to free workers if resources are at 1
        if (workers.size() > (nbases)) {
            for (int n = 0; n < (nbases); n++) {
                freeWorkers.add(workers.get(0));
                workers.remove(0);
            }
            if (defenceWorkers.size() > 2)
            {
            	for (int n = 0; n < 2; n++) {
                    defenceWorkers.add(workers.get(0));
                    workers.remove(0);
            	}
            }
            // All other workers to battle
            battleWorkers.addAll(workers);
        } else {
        	// All workers to battle
            battleWorkers.addAll(workers);
        }

        if (workers.isEmpty()) {
            return;
        }

        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbases == 0 && !freeWorkers.isEmpty()) {
            // build a base:
            if (p.getResources() >= baseType.cost) {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
                //resourcesUsed += baseType.cost;
            }
        }
        
        for (Unit u : battleWorkers) {
            meleeUnitBehavior(u, p, gs);
        }
        
        for (Unit u : defenceWorkers) {
        	meleeDefenceUnitBehavior(u, p, gs);
        }

        // harvest with all the free workers:
        for (Unit u : freeWorkers) {
            Unit closestBase = null;
            Unit closestResource = null;
            int closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isResource) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestResource == null || d < closestDistance) {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            closestDistance = 0;
            for (Unit u2 : pgs.getUnits()) {
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) {
                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                    if (closestBase == null || d < closestDistance) {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            if (closestResource != null && closestBase != null) {
                AbstractAction aa = getAbstractAction(u);
                if (aa instanceof Harvest) {
                    Harvest h_aa = (Harvest) aa;
                    if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) {
                        harvest(u, closestResource, closestBase);
                    }
                } else {
                    harvest(u, closestResource, closestBase);
                }
            }
        }
    }
    
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        int closestDistance = 0;
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy != null) {
            attack(u, closestEnemy);
        }
    }
    
    public void meleeDefenceUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit base = null;
        int closestDistance = 0;
        
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) {
            	base = u2;
            }
        }  
        for (Unit u2 : pgs.getUnits()) {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) {
                int d = Math.abs(u2.getX() - base.getX()) + Math.abs(u2.getY() - base.getY());
                if (closestEnemy == null || d < closestDistance) {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestEnemy != null) {
            attack(u, closestEnemy);
        }
    }
    
    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }
}
