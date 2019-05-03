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
    
    private Unit base = null;
    
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
        pf = new AStarPathFinding();
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
        base = null;
        
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
        	rangedUnitBehavior(u, p, gs);
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
        if (p.getResources() >= workerType.cost && nworkers <= 5)
        {
            train(u, workerType);
        }
    }
    
    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs) {
        int nbases = 0;
        int nworkers = 0;
        int nranged = 0;
        int nbarracks = 0;
        int nresources = 0;
        int workerOffset = 0; // Allocates more free workers
        
        List<Unit> freeWorkers = new LinkedList<Unit>();
        List<Unit> battleWorkers = new LinkedList<Unit>();
        
        if (workers.isEmpty()) 
        {
            return;
        }
        
        // Checks number of worker and bases the player has
        for (Unit u2 : pgs.getUnits()) 
        {
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
            // Our ranged
            if (u2.getType() == rangedType
                    && u2.getPlayer() == p.getID()) 
            {
                nranged++;
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
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()+1), (base.getY()-1) , reservedPositions, p, pgs);
                	}
                	// If negative we are on the left
                	if (xLocation <= 0)
                	{
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()-1), (base.getY()+3) , reservedPositions, p, pgs);
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
        
        // Send battle workers to battle
        for (Unit u : battleWorkers) 
        {
        	meleeUnitBehavior(u, p, gs);
        }
        
        if (battleWorkers.size() >= 3 && base != null)
        {
        	int counter = 0;
        	for(Unit u : battleWorkers)
        	{
        		System.out.println(counter);
        		if (counter >= 2)
        		{
        			
        			System.out.println("stacking battle worker");
        			stackUnits(u, gs, 0);
        		}
        		counter++;
        	}
        }
        
        // If battle worker is next to base send it back to the mines
    	//for (int n = 0; n < battleWorkers.size(); n++) 
        //{
        	//if (!battleWorkers.isEmpty()) 
        	//{
        		//if (getDistance(base, battleWorkers.get(n)) == 1)
	        	//{
	        		//freeWorkers.add(battleWorkers.get(n));
	        		//battleWorkers.remove(n);
	        	//}
        	//}
        //}

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
    
    // Checks if we are on the left of the map
    public boolean areWeOnTheLeft(PhysicalGameState pgs) {
        int baseX = base.getX();
        int xLocation = baseX - (pgs.getWidth()/2);
        // If positive we are on the right
        if (xLocation >= 0)
        {
            return false;
        }
        // If negative we are on the left
        else
        {
            return true;
        }
    }
    
    public int getDistance(Unit u, Unit u2) {
    	int distance = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
    	return distance;
    }
    
    public void meleeUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit baseEnemy = null;
        int closestDistance = 0;
        // Get closestEnemy
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (u2.getType() == baseType)
                {
                	baseEnemy = u2;
                }
                else if (closestEnemy == null || d < closestDistance) 
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
        	//boolean checkPath = pf.pathExists(u, (closestEnemy.getX()+closestEnemy.getY())*pgs.getWidth(), gs, null);
            //if (checkPath) {
            	//attack(u, closestEnemy);
            //}
            //else {
            	//if (base != null)
            	//{
            		//stackUnits(u,gs,0);
            	//}
            //}
        }
        else if (baseEnemy != null)
        {
        	attack(u, baseEnemy);
        }
    }
    
    // Stacks units up out of the way for when they can't attack
    public void stackUnits(Unit u, GameState gs, int offset) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
    	boolean onTheLeft = areWeOnTheLeft(pgs);
    	for (int n = 0; n < 5; n++)  {
        	if (onTheLeft) {
        		boolean checkPath = pf.pathExists(u, ((0 + offset)+(pgs.getHeight()-n)*pgs.getWidth()), gs, null);
        		if (checkPath) {
        			move(u, (0+offset), (pgs.getHeight()-n));
        			break;
        		}
        	}
        	else {
        		boolean checkPath = pf.pathExists(u, ((pgs.getWidth()-1-offset)+(0+n)*pgs.getWidth()), gs, null);
        		if (checkPath) {
        			move(u, ((pgs.getWidth()-1)-offset), 0+n);
        			break;
        		}
        	}
    	}
    }
    
    public void rangedUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Unit closestEnemy = null;
        Unit baseEnemy = null;
        int closestDistance = 10;

        // Get closestEnemy
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
                if (u2.getType() == baseType)
                {
                	baseEnemy = u2;
                }
                else if (closestEnemy == null || d < closestDistance) 
                {
                    closestEnemy = u2;
                    closestDistance = d;
                }
            }
        }
        // Attack if enemy exists
        if (closestEnemy != null && closestDistance >= 3) 
        {
        	attack(u, closestEnemy);
        }
        // run unless already at the spot to run too
        else if (base != null && closestDistance < 3)
        {
        	move(u, (base.getX()+1), (base.getY()+1));
        }
        else if (baseEnemy != null)
        {
        	attack(u, baseEnemy);
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
