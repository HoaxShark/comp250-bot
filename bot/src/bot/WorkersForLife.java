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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
    private UnitType lightType;
    private UnitType barracksType;
    private boolean tinyMap;

    private int rangedOrLight;
    
    public WorkersForLife(UnitTypeTable utt) 
    {
        super(new AStarPathFinding());
        this.utt = utt;
        // Set up unit types
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        rangedType = utt.getUnitType("Ranged");
        lightType = utt.getUnitType("Light");
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
        Unit base = null;
        Unit barracks = null;
        int nworkers = 0;
        
        // Define map size
        if ((pgs.getWidth() * pgs.getHeight()) <= 64)
        {
            tinyMap = true;
        }
        
        // Create lists to hold our units
        List<Unit> workers = new LinkedList<Unit>();
        List<Unit> ranged = new LinkedList<Unit>();
        List<Unit> light = new LinkedList<Unit>();
        
        // Populate our lists of units
        for (Unit u : pgs.getUnits()) 
        {
            if (u.getType().canHarvest
            		&& u.getPlayer() == player) 
            {
                workers.add(u);
                nworkers++;
            }
        	// Our base unit
        	if (u.getType().isStockpile && u.getPlayer() == p.getID()) 
            {
        		base = u;
            }
        	// Our ranged units
            if (u.getType() == rangedType
            		&& u.getPlayer() == player) 
            {
                ranged.add(u);
            }
            // Our light units
            if (u.getType() == lightType
            		&& u.getPlayer() == player) 
            {
                light.add(u);
            }
        }
        
        // Behaviour of workers:
        workersBehavior(workers, p, pgs, gs, base);
        
        // Behaviour of ranged:
        for (Unit u : ranged) 
        {
        	battleUnitBehavior(u, p, gs);
        	if (barracks != null)
        	{
	        	if (getDistance(barracks, u) == 1)
	        	{
	        		stackUnits(u, gs, 1, base);
	        	}
        	}
        }
        
        // Behaviour of light:
        for (Unit u : light) 
        {
        	battleUnitBehavior(u, p, gs);
        }
        
        // Search the unit list again to apply behaviour for bases and barracks
        for (Unit u : pgs.getUnits()) 
        {
        	// Behaviour of bases:
            if (u.getType() == baseType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) 
            {
            	baseBehavior(u, p, nworkers);
            }
            
            // Behaviour of barracks:
            if (u.getType() == barracksType
                    && u.getPlayer() == player
                    && gs.getActionAssignment(u) == null) 
            {
                // If enough resources train ranged unit
                if(p.getResources() >= rangedType.cost && rangedOrLight == 0)
                {
                	train(u, rangedType);
                	rangedOrLight = 1;
                }
                // If enough resources train light unit
                else if(p.getResources() >= lightType.cost && rangedOrLight == 1)
                {
                	train(u, lightType);
                	rangedOrLight = 0;
                }
            }
        }
                       
        return translateActions(player, gs);
    }
    
    /*================Behaviours==============*/
    
    // Basic base behaviour
    public void baseBehavior(Unit u, Player p, int ourWorkers) {
        int nworkers = ourWorkers;
        
        // If we can afford a worker and we have 5 or less build a new worker
        if (p.getResources() >= workerType.cost && nworkers <= 5)
        {
            train(u, workerType);
        }
    }
    
    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs, Unit base) {
        int nbases = 0;
        int nbarracks = 0;
        int nresources = 0;
        int workerOffset = 0; // Allocates more free workers
        
        // Workers that can be used for harvesting and building
        List<Unit> freeWorkers = new LinkedList<Unit>();
        // Workers that can be send to fight
        List<Unit> battleWorkers = new LinkedList<Unit>();
        
        // If the worker list is empty return
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
        if (nresources <= 0)
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
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()+2), (base.getY()-2) , reservedPositions, p, pgs);
                	}
                	// If negative we are on the left
                	if (xLocation <= 0)
                	{
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()-2), (base.getY()+4) , reservedPositions, p, pgs);
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
        	battleUnitBehavior(u, p, gs);
        }
        
        if (battleWorkers.size() >= 3 && base != null)
        {
        	int counter = 0;
        	for(Unit u : battleWorkers)
        	{
        		if (counter >= 2)
        		{
        			stackUnits(u, gs, 0, base);
        		}
        		counter++;
        	}
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
    
    public void battleUnitBehavior(Unit u, Player p, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        // Get the closest enemy unit and base
        Map<String, Unit> closestBaseAndEnemy = getClosestEnemy(pgs, u, p);
        // Allocate unit and base to variables to avoid additional look ups
        Unit closestEnemy = closestBaseAndEnemy.get("enemy");
        Unit baseEnemy = closestBaseAndEnemy.get("base"); 
        
        // Attack if enemy unit exists
        if (closestEnemy != null) 
        {
        	attack(u, closestEnemy);
        }
        // If no enemy units try to attack bases
        else if (baseEnemy != null)
        {
        	attack(u, baseEnemy);
        }
    }
    
    /*===================Utility=============*/
    
    // Gets the closest enemy and base to the given unit
    private Map<String, Unit> getClosestEnemy(PhysicalGameState pgs, Unit u, Player p)
    {
    	Map<String, Unit> closestBaseAndEnemy = new HashMap<String, Unit>();
        int closestDistance = 10;
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = getDistance(u,u2);
                if (u2.getType() == baseType)
                {
                	closestBaseAndEnemy.put("base",u2);
                }
                else if (closestBaseAndEnemy.get("enemy") == null || d < closestDistance) 
                {
                	closestBaseAndEnemy.put("enemy",u2);
                    closestDistance = d;
                }
            }
        }
        return closestBaseAndEnemy;
    }
    
    // Checks if we are on the left of the map
    private boolean areWeOnTheLeft(PhysicalGameState pgs, Unit base) {
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
    
    private int getDistance(Unit u, Unit u2) {
    	int distance = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
    	return distance;
    }
    
    // Stacks units up out of the way for when they can't attack
    private void stackUnits(Unit u, GameState gs, int offset, Unit base) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
    	boolean onTheLeft = areWeOnTheLeft(pgs, base);
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
    
    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }
}
