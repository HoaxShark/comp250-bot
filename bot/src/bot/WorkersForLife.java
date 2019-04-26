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

public class WorkersForLife extends AbstractionLayerAI 
{    
    private UnitTypeTable utt;
    private UnitType workerType;
    private UnitType baseType;
    private UnitType rangedType;
    private UnitType barracksType;
    private boolean tinyMap;
    
    public WorkersForLife(UnitTypeTable utt) 
    {
        super(new AStarPathFinding());
        this.utt = utt;
        // Set up unit types
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        rangedType = utt.getUnitType("Ranged");
        barracksType = utt.getUnitType("Barracks");
        // Set up map size variable
        tinyMap = false;
    }
    

    @Override
    public void reset() 
    {
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        rangedType = utt.getUnitType("Ranged");
        barracksType = utt.getUnitType("Barracks");
        tinyMap = false;
    }

    
    @Override
    public AI clone() 
    {
        return new WorkersForLife(utt);
    }
   
    
    @Override
    public PlayerAction getAction(int player, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        Unit base = null;
        
        // Define map size
        if ((pgs.getWidth() * pgs.getHeight()) <= 64)
        {
            tinyMap = true;
        }
        
        // Populate worker list
        List<Unit> workers = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType().canHarvest
            		&& u.getPlayer() == player) 
            {
                workers.add(u);
            }
        	// Our base unit
        	if (u.getType().isStockpile && u.getPlayer() == p.getID()) 
            {
        		base = u;
            }
        }
        
        // Behaviour of workers:
        workersBehavior(workers, p, pgs, gs);

        // Populate our ranged list
        List<Unit> ranged = new LinkedList<Unit>();
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType() == rangedType
            		&& u.getPlayer() == player) 
            {
                ranged.add(u);
            }
        }
        // Behaviour of ranged:
        for (Unit u : ranged) 
        {
        	rangedUnitBehavior(u, base, p, gs);
        }
        
        // Behaviour of bases:
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) 
            {
            	baseBehavior(u, p, pgs);
            }
        }
        
        // Behaviour of barracks:
        for (Unit u : pgs.getUnits()) 
        {
        	// If our barracks and no current actions
            if (u.getType() == barracksType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) 
            {
                // If enough resources train ranged unit
                if(p.getResources() >= rangedType.cost)
                {
                	train(u, rangedType);
                }
            }
        }
                       
        return translateActions(player, gs);
    }
    
    // Basic base behaviour will keep building workers
    public void baseBehavior(Unit u, Player p, PhysicalGameState pgs) {

        int nbases = 0;
        int nworkers = 0;
        int enemyWorkers = 0;

        for (Unit u2 : pgs.getUnits()) {
        	// Our workers
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) {
                nworkers++;
            }
            // EnemyWorkers
            if (u2.getType() == workerType
                    && u2.getPlayer() != p.getID()) {
                enemyWorkers++;
            }
            // Our bases
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) {
                nbases++;
            }
        }
        if (p.getResources() >= workerType.cost
        		&& nworkers <= enemyWorkers) 
        {
            train(u, workerType);
        }
    }
    
    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs) {
        int nbases = 0;
        int nworkers = 0;
        int nbarracks = 0;
        int nresources = 0;
        int workerOffset = 0; // Allocates more free workers
        Unit base = null;
        
        List<Unit> freeWorkers = new LinkedList<Unit>();
        List<Unit> battleWorkers = new LinkedList<Unit>();
        
        if (workers.isEmpty()) 
        {
            return;
        }
        
        // Checks number of worker and bases the player has
        for (Unit u2 : pgs.getUnits()) 
        {
        	// Our base unit
        	if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) 
            {
        		base = u2;
            }
        	// Our bases
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) 
            {
                nbases++;
            }
            // Our barracks
            if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) 
            {
                nbarracks++;
            }
            // Our workers
            if (u2.getType() == workerType
                    && u2.getPlayer() == p.getID()) 
            {
                nworkers++;
            }
            // Resource piles on the map
            if (u2.getType().isResource) 
            {
            	nresources++;
            }
        }
        
        // If playing on a bigger map have more free workers
        if (!tinyMap)
        {
        	workerOffset = 1;
        }
        
        // If no resources left to be gathered
        if (nresources == 0)
        {
        	for (int n = 0; n < workers.size(); n++) 
            {
            	if (!workers.isEmpty())
            	{
            		battleWorkers.add(workers.get(0));
                    workers.remove(0);
            	}
            }
        }
        
        // Applies workers for each base to free workers
        if (workers.size() >= (nbases) && nresources !=0)
        {
            for (int n = 0; n < (nbases + workerOffset); n++) 
            {
            	if (!workers.isEmpty())
            	{
                    freeWorkers.add(workers.get(0));
                    workers.remove(0);
            	}
            }
            // All other workers to battle
            battleWorkers.addAll(workers);
        } 

        List<Integer> reservedPositions = new LinkedList<Integer>();
        if (nbarracks == 0 && !freeWorkers.isEmpty())
        {
        	// Build a barracks
            if (p.getResources() >= 6) {
            	Unit u = freeWorkers.remove(0);
            	// subtract half the width from the base location
            	if (base != null)
            	{
                	int baseX = base.getX();
                	int xLocation = baseX - (pgs.getWidth()/2);
                	// If positive we are on the right
                	if (xLocation >= 0)
                	{
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()-1), (base.getY()-1) , reservedPositions, p, pgs);
                	}
                	// If negative we are on the left
                	if (xLocation <= 0)
                	{
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()+1), (base.getY()+1) , reservedPositions, p, pgs);
                	}
            	}
            	if (base == null)
            	{
                    buildIfNotAlreadyBuilding(u, barracksType, u.getX(), u.getY(), reservedPositions, p, pgs);
            	}
            }
        }
        
        if (nbases == 0 && !freeWorkers.isEmpty()) 
        {
            // Build a base:
            if (p.getResources() >= baseType.cost) 
            {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
            }
        }
        
        for (Unit u : battleWorkers) 
        {
        	meleeUnitBehavior(u, p, gs);
        }

        // Harvest with all the free workers:
        if (nresources != 0) 
        {
	        for (Unit u : freeWorkers) 
	        {
	            Unit closestBase = null;
	            Unit closestResource = null;
	            int closestDistance = 0;
	            // Get closest resource
	            for (Unit u2 : pgs.getUnits()) 
	            {
	                if (u2.getType().isResource) 
	                {
	                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
	                    if (closestResource == null || d < closestDistance) 
	                    {
	                        closestResource = u2;
	                        closestDistance = d;
	                    }
	                }
	            }
	            // Reset closestDistance
	            closestDistance = 0;
	            // Get closestBase
	            for (Unit u2 : pgs.getUnits()) 
	            {
	                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) 
	                {
	                    int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
	                    if (closestBase == null || d < closestDistance) 
	                    {
	                        closestBase = u2;
	                        closestDistance = d;
	                    }
	                }
	            }
	            // If we have a base and resource then harvest!
	            if (closestResource != null && closestBase != null) 
	            {
	            	//harvest(u, closestResource, closestBase);
	                AbstractAction aa = getAbstractAction(u);
	                if (aa instanceof Harvest) 
	                {
	                    Harvest h_aa = (Harvest) aa;
	                    if (h_aa.getTarget() != closestResource || h_aa.getBase() != closestBase) 
	                    {
	                        harvest(u, closestResource, closestBase);
	                    }
	                } 
	                else 
	                {
	                    harvest(u, closestResource, closestBase);
	                }
	            }
	        }
        }
    }
    
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        int closestDistance = 0;
        // Get closestEnemy
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) 
                {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        // Attack if enemy exists
        if (closestEnemy != null) 
        {
            attack(u, closestEnemy);
        }
    }
    
    public void rangedUnitBehavior(Unit u, Unit base, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        boolean running = false;
        int closestDistance = 0;
        // Get closestEnemy
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (closestEnemy == null || d < closestDistance) 
                {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        // Attack if enemy exists
        if (closestEnemy != null && closestDistance >= 3) 
        {
        	//System.out.println(closestDistance);
            attack(u, closestEnemy);
        }
        else if (base != null)
        {
        	//System.out.println("runnings");
        	move(u, (base.getX()+1), (base.getY()+1));
        }
        else
        {
        	attack(u, closestEnemy);
        }
    }
    
    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }
}
