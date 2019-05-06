package bot;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import rts.*;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

/** An AI for MicroRTS, this bot focuses on countering worker rushes and trying to build
 * a barracks when possible to get ranged and light units out.
 * @author HoaxShark
 */
public class WorkersForLife extends AbstractionLayerAI 
{    
    private UnitTypeTable utt;
    private UnitType workerType;
    private UnitType baseType;
    private UnitType rangedType;
    private UnitType lightType;
    private UnitType barracksType;

    private int rangedOrLight; /**< Is used to decide between the barracks making a ranged or light unit, 
    							when set to 0 or 1 respectively. Is an int so it can expanded upon for other unit types if desired */
    
    public WorkersForLife(UnitTypeTable utt) 
    {
        super(new AStarPathFinding());
        this.utt = utt;
        /// Set up unit types
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        rangedType = utt.getUnitType("Ranged");
        lightType = utt.getUnitType("Light");
        barracksType = utt.getUnitType("Barracks");
        /// Set up path finding so we can call its functions
        pf = new AStarPathFinding();
    }
    

    @Override
    public void reset() 
    {
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        rangedType = utt.getUnitType("Ranged");
        lightType = utt.getUnitType("Light");
        barracksType = utt.getUnitType("Barracks");
        pf = new AStarPathFinding();
    }

    
    @Override
    public AI clone() 
    {
        return new WorkersForLife(utt);
    }
   
    /** Called each tick this is the main body of the AI.
     * Populates lists of various units and tells what to do depending on the state of the match.
     */
    @Override
    public PlayerAction getAction(int player, GameState gs) 
    {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        Player p = gs.getPlayer(player);
        Unit base = null; ///< Our base, we will only ever have one currently
        Unit barracks = null; ///< Our barracks, we will only ever have one currently
        int nworkers = 0; ///< How many workers we have
               
        /// Create lists to hold our units
        List<Unit> workers = new LinkedList<Unit>();
        List<Unit> ranged = new LinkedList<Unit>();
        List<Unit> light = new LinkedList<Unit>();
        
        /// Populate our lists and variables of units
        for (Unit u : pgs.getUnits()) 
        {
        	/// Our workers
            if (u.getType().canHarvest && u.getPlayer() == player) 
            {
                workers.add(u);
                nworkers++;
            }
        	/// Our base unit
        	if (u.getType().isStockpile && u.getPlayer() == p.getID()) 
            {
        		base = u;
            }
        	/// Our ranged units
            if (u.getType() == rangedType && u.getPlayer() == player) 
            {
                ranged.add(u);
            }
            /// Our light units
            if (u.getType() == lightType && u.getPlayer() == player) 
            {
                light.add(u);
            }
            /// Our barracks
            if (u.getType() == barracksType && u.getPlayer() == player)
            {
            	barracks = u;
            }
            /// Our bases
            if (u.getType() == baseType && u.getPlayer() == player)
            {
            	base = u;
            }
        }
        
        /// Apply behaviour to workers
        workersBehavior(workers, p, pgs, gs, base);
        
        /// Cycle through all ranged units and apply behaviour
        for (Unit u : ranged) 
        {
        	battleUnitBehavior(u, p, pgs);
        	/// If this unit is getting stuck next to the barracks, it will be stacked up
        	if (barracks != null && base != null)
        	{
	        	if (getDistance(barracks, u) == 1)
	        	{
	        		stackUnits(u, gs, 1, base, 5);
	        	}
        	}
        }
        
        /// Cycle through all light units and apply behaviour
        for (Unit u : light) 
        {
        	battleUnitBehavior(u, p, pgs);
        }
        
    	/// If our base is not building something then apply base behaviour
        if (base != null && gs.getActionAssignment(base) == null) 
        {
        	baseBehavior(base, p, nworkers, 5);
        }
        
        /// If our barracks is not building something then apply base behaviour
        if (barracks != null && gs.getActionAssignment(barracks) == null) 
        {
            barracksBehaviour(barracks, p);
        }
                              
        return translateActions(player, gs);
    }
    
    /*================Behaviours==============*/
    
    /** Behaviour for barracks.
     * Called when the barracks is not building anything, will try to build light or ranged units.
     * Expand this behaviour if wanting to bring in different unit types or build orders.
     * @param barracks Our barracks
     * @param p Our player
     */
    public void barracksBehaviour(Unit barracks, Player p) {
    	/// If enough resources train ranged unit
        if(p.getResources() >= rangedType.cost && rangedOrLight == 0)
        {
        	train(barracks, rangedType);
        	/// Set the next unit to be built as light
        	rangedOrLight = 1;
        }
        /// If enough resources train light unit
        else if(p.getResources() >= lightType.cost && rangedOrLight == 1)
        {
        	train(barracks, lightType);
        	/// Set the next unit to be built as ranged
        	rangedOrLight = 0;
        }
    }
    
    /** Behaviour for base.
     * Builds new workers if we have the resources and are not over the maxWorkers
     * @param base Our base
     * @param p Our player
     * @param ourWorkers Number of workers we have
     * @param maxWorkers Max number of workers we want to have
     */
    public void baseBehavior(Unit base, Player p, int ourWorkers, int maxWorkers) {      
        /// If we can afford a worker and we have maxWorkers or less build a new worker
        if (p.getResources() >= workerType.cost && ourWorkers <= maxWorkers)
        {
            train(base, workerType);
        }
    }
    
    /** Behaviour for workers.
     * Takes the list of workers and delegates them to either free or battle workers depending on the current state of the game
     * Then applies behaviours to those individual lists. Here we deal with harvesting, and building barracks or bases.
     * @param workers List of all our workers
     * @param p Our player
     * @param pgs The PhysicalGameState
     * @param gs The GameState
     * @param base Our base
     */
    public void workersBehavior(List<Unit> workers, Player p, PhysicalGameState pgs, GameState gs, Unit base) {
        int nbases = 0; /**< Number of bases we have, currently will only ever be 0 or 1 but has been designed
        					with the idea of expansion in mind. */
        int nbarracks = 0; /**< Number of barracks we have, currently will only ever be 0 or 1 but has been designed
								with the idea of expansion in mind. */
        int nworkers = workers.size(); ///< Number of workers we have
        int nresources = 0;	///< Number of resource piles on the map
        int workerOffset = 0; ///< Allocates more free workers set to higher for bigger maps
        
        List<Unit> freeWorkers = new LinkedList<Unit>(); ///< Workers that can be used for harvesting and building
        List<Unit> battleWorkers = new LinkedList<Unit>(); ///< Workers that can be send to fight
        
        /// If the worker list is empty return
        if (workers.isEmpty()) 
        {
            return;
        }
        
        /// Check number of worker and bases the player has and the resources piles on the map
        for (Unit u2 : pgs.getUnits()) 
        {
        	/// Our bases
            if (u2.getType() == baseType
                    && u2.getPlayer() == p.getID()) 
            {
                nbases++;
            }
            /// Our barracks
            if (u2.getType() == barracksType
                    && u2.getPlayer() == p.getID()) 
            {
                nbarracks++;
            }
            /// Resource piles on the map
            if (u2.getType().isResource) 
            {
            	nresources++;
            }
        }
        
        /// If playing on a bigger map have more free workers
        if ((pgs.getWidth() * pgs.getHeight()) > 64)
        {
        	workerOffset = 1;
        }
        
        /// If no resources left to be gathered send all workers to battle
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
        
        /// Applies workers for each base to free workers
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
            /// All other workers to battle
            battleWorkers.addAll(workers);
        } 

        List<Integer> reservedPositions = new LinkedList<Integer>(); ///< List of reserved building positions
        /// Consider building a barracks
        if (nbarracks == 0 && !freeWorkers.isEmpty())
        {
        	/// Build a barracks if we have enough resources
            if (p.getResources() >= 6 && nworkers >= 4) {
            	Unit u = freeWorkers.remove(0);
            	/// Do a null check on the base 
            	if (base != null)
            	{
            		/// Check which side of the map we are on
            		boolean leftSide = areWeOnTheLeft(pgs, base);
            		/// Build a barracks in the set locations depending on our side of the map
                	if (!leftSide)
                	{
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()+2), (base.getY()-2) , reservedPositions, p, pgs);
                	}
                	if (leftSide)
                	{
                        buildIfNotAlreadyBuilding(u, barracksType, (base.getX()-2), (base.getY()+4) , reservedPositions, p, pgs);
                	}
            	}
            }
        }
        
        /// If our base dies try to replace it
        if (nbases == 0 && !freeWorkers.isEmpty()) 
        {
            /// Build a base
            if (p.getResources() >= baseType.cost) 
            {
                Unit u = freeWorkers.remove(0);
                buildIfNotAlreadyBuilding(u, baseType, u.getX(), u.getY(), reservedPositions, p, pgs);
            }
        }
        
        /// Send battle workers to battle
        for (Unit u : battleWorkers) 
        {
        	battleUnitBehavior(u, p, pgs);
        }
        
        /// If we have a certain number of battleWorkers start stacking some out of the way
        if (battleWorkers.size() >= 3 && base != null)
        {
        	int counter = 0;
        	for(Unit u : battleWorkers)
        	{
        		if (counter >= 2)
        		{
        			stackUnits(u, gs, 0, base, 5);
        		}
        		counter++;
        	}
        }

        /// Harvest with all the free workers, do this last.
        if (nresources != 0) 
        {
	        workerHarvest(freeWorkers, pgs, p);
        }
    }
    
    /** Battle unit behaviour.
     * Used to send a unit to fight, gets the closest base and enemy unit.
     * Prioritises killing units over bases, always aiming for the closest one.
     * @param u Our unit we are commanding
     * @param p Our player
     * @param pgs The GameState
     */
    public void battleUnitBehavior(Unit u, Player p, PhysicalGameState pgs) {
        /// Get the closest enemy unit and base
        Map<String, Unit> closestBaseAndEnemy = getClosestEnemy(pgs, u, p);
        /// Allocate unit and base to variables to avoid additional look ups
        Unit closestEnemy = closestBaseAndEnemy.get("enemy");
        Unit baseEnemy = closestBaseAndEnemy.get("base"); 
        
        /// Attack if enemy unit exists
        if (closestEnemy != null) 
        {
        	attack(u, closestEnemy);
        }
        /// If no enemy units try to attack bases
        else if (baseEnemy != null)
        {
        	attack(u, baseEnemy);
        }
    }
    
    /** Tells workers in a list to harvest resources.
     * Gets the closest resource pile and closest base and orders workers to harvest between them.
     * @param freeWorkers List of workers we want to harvest
     * @param pgs The PhysicalGameSatet
     * @param p Our player
     */
    private void workerHarvest(List<Unit> freeWorkers, PhysicalGameState pgs, Player p)
    {
    	for (Unit u : freeWorkers) 
        {
            Unit closestBase = null; ///< The closest base to the worker
            Unit closestResource = null; ///< The closest resource pile to the worker
            int closestDistance = 0; ///< Current closest distance
            /// Get closest base
            for (Unit u2 : pgs.getUnits()) 
            {
                if (u2.getType().isStockpile && u2.getPlayer() == p.getID()) 
                {
                    int d = getDistance(u,u2);
                    if (closestBase == null || d < closestDistance) 
                    {
                        closestBase = u2;
                        closestDistance = d;
                    }
                }
            }
            /// Reset closestDistance
            closestDistance = 0;
            /// Get closest resource
            for (Unit u2 : pgs.getUnits()) 
            {
                if (u2.getType().isResource) 
                {
                    int d = getDistance(u,u2);
                    if (closestResource == null || d < closestDistance) 
                    {
                        closestResource = u2;
                        closestDistance = d;
                    }
                }
            }
            /// If we have a base and resource then harvest, so long as the resource isn't too far away
            if (closestResource != null && closestBase != null && closestDistance <= 4) 
            {
            	harvest(u, closestResource, closestBase);
            }
            else
            {
            	battleUnitBehavior(u,p,pgs);
            }
        }
    }
    
    /*===================Utility===============*/
    
    /** Gets the closest enemy unit and enemy base.
     * Finds the closest enemy to the given unit, looks for units and bases and returns both the closest
     * unit and base in a Map format
     * @param pgs The PhysicalGameState
     * @param u The unit to check from
     * @param p Our player
     * @return Returns a Map with [key:unit] keys are "enemy" or "base"
     */
    private Map<String, Unit> getClosestEnemy(PhysicalGameState pgs, Unit u, Player p)
    {
    	Map<String, Unit> closestBaseAndEnemy = new HashMap<String, Unit>(); ///< Map to hold the enemy and base
        int closestDistance = 10; ///< The current closest distance
        /// Populate the closestBaseAndEnemy Map
        for (Unit u2 : pgs.getUnits()) 
        {
            if (u2.getPlayer() >= 0 && u2.getPlayer() != p.getID()) 
            {
                int d = getDistance(u,u2);
                /// If the unit is a base
                if (u2.getType() == baseType)
                {
                	closestBaseAndEnemy.put("base",u2);
                }
                /// If the unit is another type of enemy
                else if (closestBaseAndEnemy.get("enemy") == null || d < closestDistance) 
                {
                	closestBaseAndEnemy.put("enemy",u2);
                    closestDistance = d;
                }
            }
        }
        return closestBaseAndEnemy;
    }
    
    /** Check what side of the map we are on.
     * Returns true if we are on the left side of the map, and false if we are on the right
     * Subtracts half the map width from our base's X position, if positive we are on the right
     * @param pgs The PhysicalGameState
     * @param base Our base
     * @return True if on the left, false if on the right
     */
    private boolean areWeOnTheLeft(PhysicalGameState pgs, Unit base) {
    	/// Get the bases's X position
        int baseX = base.getX();
        /// Get half the map width
        int xLocation = baseX - (pgs.getWidth()/2);
        /// If positive we are on the right
        if (xLocation >= 0)
        {
            return false;
        }
        /// If negative we are on the left
        else
        {
            return true;
        }
    }
    
    /** Gets the distance between two units.
     * Checks the differences between X and Y positions of two units and adds them together to return a single int
     * @param u The first unit to check
     * @param u2 The second unit to check
     * @return The distance value as a single int
     */
    private int getDistance(Unit u, Unit u2) {
    	int distance = Math.abs(u2.getX() - u.getX()) + Math.abs(u2.getY() - u.getY());
    	return distance;
    }
    
    /** Stacks units up out of the way for when they can't attack.
     * Used when we have units not doing anything to get them out the way of the base or barracks.
     * Checks if we are on the left or right then stacks the units below of above the base respectively.
     * Checks that there is a path that the unit can use to get to that location, this avoids units trying to go
     * to already used locations for stacking.
     * @param u The unit we want to stack
     * @param gs The GameState
     * @param offset This offset moves the Y value of stacking position so units can be stacked in different columns
     * @param base Our base
     * @param loops The numbers of tries to stack a unit, each tries moves the x value by one to find a free space
     */
    private void stackUnits(Unit u, GameState gs, int offset, Unit base, int loops) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        /// Check what side of the map we are on
    	boolean onTheLeft = areWeOnTheLeft(pgs, base);
    	/// Try to stack unit on the correct side if a path exists
    	for (int n = 0; n < loops; n++)  {
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
