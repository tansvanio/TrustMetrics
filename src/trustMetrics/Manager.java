package trustMetrics;
import java.awt.Color;
import java.util.*;

import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.essentials.RepastEssentials;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.util.ContextUtils;
import repast.simphony.visualizationOGL2D.DefaultStyleOGL2D;
import sajas.core.Agent;
import jade.core.AID;
import sajas.core.behaviours.Behaviour;
import sajas.core.behaviours.SimpleBehaviour;
import sajas.core.behaviours.WakerBehaviour;
import sajas.core.behaviours.WrapperBehaviour;
import sajas.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import sajas.proto.ContractNetInitiator;
import sajas.proto.SubscriptionInitiator;
public class Manager extends Agent{
	private Context<?> context;
	private Network<Object> net;
	private RepastEssentials re = new RepastEssentials();
	
	private HashSet<Task> projectTasks;
	private static ArrayList<Task> criticalPath;
	private ArrayList<Task> availableTasks;
	private ArrayList<Worker> workerList;
	String name;
	public Manager(String name,Task... tasks)
	{
		this.name = name;
		projectTasks = new HashSet<Task>();
		criticalPath = new ArrayList<Task>();
		availableTasks = new ArrayList<Task>();
		workerList = new ArrayList<Worker>();
		for(Task t: tasks)
		{
			projectTasks.add(t);
		}
		
	}
	public double getFinishedTasks()
	{
		int f = 0;
		for(Task t: criticalPath)
		{
			if(t.getFinished()) f++;
		}
		return f;
	}
	public void setup()
	{
		DFAgentDescription template = new DFAgentDescription();
		template.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setName(getName());
		sd.setType("Manager");
		template.addServices(sd);
		 try {
	         DFService.register(this, template);
	      } catch(FIPAException e) {
	         e.printStackTrace();
	      }
		 criticalPathBehaviour criticalPathBehaviour = new criticalPathBehaviour();
		 availableTasksBehaviour availableTasksBehaviour = new availableTasksBehaviour();
		 addBehaviour(criticalPathBehaviour);
		 addBehaviour(availableTasksBehaviour);
		
	}
	public void addWorkers(Worker... workers)
	{
		for(Worker w: workers)
		{
			workerList.add(w);
		}
	}
	private class criticalPathBehaviour extends SimpleBehaviour
	{
		boolean d = false;
		@Override
		public void action() {
			
			criticalPath.clear();
		    for(Task t: criticalPath(projectTasks))
		    {
		    	criticalPath.add(t);
		    }
			System.out.println("Manager - Critical Path: "+Arrays.toString(criticalPath(projectTasks)));
			d = true;
			
			
		}
		@Override
		public boolean done() {
			// TODO Auto-generated method stub
			return d;
		}
		
	}
	private class availableTasksBehaviour extends SimpleBehaviour
	{
		boolean d = false;
		@Override
		public void action() {
			//For each task in the project.
			availableTasks.clear();
			for(Task t: projectTasks)
			{
				//We check if any of its dependencies is not finished.
				boolean dependenciesLeft = false;
				for(Task T: projectTasks)
				{
					//If we find a dependency that is not finished, then that task is not available to be worked on.
					if(T.getDependencies().contains(t) && T.getFinished() == false) dependenciesLeft = true;
				}
				//If no dependencies left unfinished, we can work on it.
				if(!dependenciesLeft && !t.getFinished()) 
				{
					if(t.getNamePrivate().equals("End"))
					{
						t.setAvailable();
						t.completion = 100;
						System.out.println("Manager - Project Complete. "+ "("+(re.GetTickCount()/ 10)+" weeks).");
						printWorkerKnowledge();
						d = true;
						RunEnvironment.getInstance().endRun();
						
					}
					else
					{
						t.setAvailable();
						availableTasks.add(t);
					}
				}
			}
			allocateWorkersSmart();		
		}
		@Override
		public boolean done() {
			// TODO Auto-generated method stub
			return d;
		}
		public void allocateWorkersSmart()
		{
			for(Task t: criticalPath)
			{
				if(availableTasks.contains(t))
				{
					//Add workers to tasks they are good at.
					for(Worker w: workerList)
					{
						if(!w.assigned)
						{
							//System.out.println(w.getNamePrivate() + " Value:" + t.getExpectedWorkerValue(w));
							if(t.getExpectedWorkerValue(w) >= 0 && !t.isHealthy())
							{
								t.addWorker(w);
							}
						}
					}
					/*One the first iteration, if the critical task is not healthy, add workers
					despite their value until it is.
					*/
					if(criticalPath.indexOf(t) == 0 && !t.isHealthy())
					{
						for(Worker w: workerList)
						{
							//More effective if workers in workerlist are ordered by value to ask t.
							if(!w.assigned) t.addWorker(w);
							if(t.isHealthy()) break;
						}
					}
				}
			}
			//Add the remainder of the workers to the most important task. (First in the list.)
			for(Task t: criticalPath)
			{
				if(availableTasks.contains(t))
				{
					for(Worker w: workerList)
					{
						if(!w.assigned && t.getExpectedWorkerValue(w) > -1) t.addWorker(w);
					}
				}
			}
		}
		public void printWorkerKnowledge()
		{
			for(Worker w: workerList)
			{
				System.out.println(w.getNamePrivate() + " Report");
				for (String s : w.getSkillSet().keySet()) {
					System.out.println("Skill: " + s + " Knowledge: "+w.getSkillValue_RWSV(s)+" Real: "+w.getSkillSet().get(s));
				}
			}
		}
		
	}
	public static Task[] criticalPath(Set<Task> tasks){
	    //tasks whose critical cost has been calculated
	    HashSet<Task> completed = new HashSet<Task>();
	    //tasks whose ciritcal cost needs to be calculated
	    HashSet<Task> remaining = new HashSet<Task>(tasks);

	    //Backflow algorithm
	    //while there are tasks whose critical cost isn't calculated.
	    while(!remaining.isEmpty()){
	      boolean progress = false;

	      //find a new task to calculate
	      for(Iterator<Task> it = remaining.iterator();it.hasNext();){
	        Task task = it.next();
	        if(completed.containsAll(task.getDependencies())){
	          //all dependencies calculated, critical cost is max dependency
	          //critical cost, plus our cost
	          int critical = 0;
	          for(Task t : task.getDependencies()){
	            if(t.getCriticalCost() > critical){
	              critical = t.getCriticalCost();
	            }
	          }
	          task.setCriticalCost(critical+task.getCost());
	          //set task as calculated an remove
	          completed.add(task);
	          it.remove();
	          //note we are making progress
	          progress = true;
	        }
	      }
	      //If we haven't made any progress then a cycle must exist in
	      //the graph and we wont be able to calculate the critical path
	      if(!progress) throw new RuntimeException("Cyclic dependency, algorithm stopped!");
	    }

	    //get the tasks
	    Task[] ret = completed.toArray(new Task[0]);
	    //create a priority list
	    Arrays.sort(ret, new Comparator<Task>() {

	      @Override
	      public int compare(Task o1, Task o2) {
	        //sort by cost
	        int i= o2.getCriticalCost()-o1.getCriticalCost();
	        if(i != 0)return i;

	        //using dependency as a tie breaker
	        //note if a is dependent on b then
	        //critical cost a must be >= critical cost of b
	        if(o1.isDependent(o2))return -1;
	        if(o2.isDependent(o1))return 1;
	        return 0;
	      }
	    });
	    return ret;
	  }
}
