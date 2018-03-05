package com.graphhopper.jsprit.examples;

import com.graphhopper.jsprit.analysis.toolbox.GraphStreamViewer;
import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.TaiPingConstraint;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.UnassignedJobReasonTracker;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import com.graphhopper.jsprit.util.Examples;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author huangrun (huangrun@shanshu.ai)
 * @date 2018/03/01
 */
public class TaiPing {

    public static void main(String[] args) {
        double lng_factor = 1;
        double lat_factor = 1;
        /*
         * some preparation - create output folder
		 */
        Examples.createOutputFolder();
        createInputFolder();

        List<List<String>> matrix = read("jinan_DM.csv");

        Map<String, Location> locations = new HashMap<>(200);

        VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);
        for(List<String> record : matrix){
            Location location = Location.Builder.newInstance()
                .setId(record.get(0))
                .setName(record.get(0))
                .setCoordinate(Coordinate.newInstance(lng_factor * Double.parseDouble(record.get(1)), lat_factor * Double.parseDouble(record.get(2))))
                .build();
            locations.put(record.get(0), location);
            costMatrixBuilder.addTransportDistance(record.get(0), record.get(4), Double.parseDouble(record.get(8)));
            costMatrixBuilder.addTransportTime(record.get(0), record.get(4), Double.parseDouble(record.get(9)));
        }

//        for(Location loc : locations.values()){
//            System.out.println("{\n" + "icon: 'http://webapi.amap.com/theme/v1.3/markers/n/mark_b1.png',\n"
//                                   + "position: ["+loc.getCoordinate().getX()+","+loc.getCoordinate().getY()+"],\n" +
//                                   "content:\"" + loc.getName() + "\"},");
//        }

        VehicleRoutingTransportCosts costMatrix = costMatrixBuilder.build();

		/*
         * get a vehicle type-builder and build a type with the typeId "vehicleType" and a capacity of 2
		 */
        VehicleTypeImpl.Builder vehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance("vehicleType")
            // 暂定一天最多接待50个
            .addCapacityDimension(0,50)
            .setFixedCost(100);
        VehicleType vehicleType = vehicleTypeBuilder.build();

		/*
         * get a vehicle-builder and build a vehicle located at "0" with type "vehicleType"
		 */
        VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance("vehicle");
        vehicleBuilder.setStartLocation(locations.get("0"));
        vehicleBuilder.setType(vehicleType);
        vehicleBuilder.setReturnToDepot(false);

        VehicleImpl vehicle = vehicleBuilder.build();

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.addVehicle(vehicle);
        vrpBuilder.setRoutingCost(costMatrix);

		/*
         * build services at the required locations, each with a capacity-demand of 1.
		 */
		List<List<String>> orders = read("TaipingOrders.csv");
        for(List<String> record : orders){
            String id = record.get(0);
            int groupId = Integer.parseInt(record.get(1));
            String pickupLocId = record.get(4);
            String deliveryLocId = record.get(5);
            double deliveryServiceTime = Double.parseDouble(record.get(6)) * 60;
            Shipment.Builder spBuilder = Shipment.Builder.newInstance(id)
                .setTaipingGroup(groupId)
                .addSizeDimension(0,1)
                .setPickupLocation(locations.get(pickupLocId))
                .setDeliveryLocation(locations.get(deliveryLocId))
                .setDeliveryServiceTime(deliveryServiceTime);
//                .addDeliveryTimeWindow(0,21600)
//                .addDeliveryTimeWindow(86400,108000)
//                .addDeliveryTimeWindow(172800,194400)
//                .addPickupTimeWindow(0,21600)
//                .addPickupTimeWindow(86400,108000)
//                .addPickupTimeWindow(172800,194400)
            int num;
            switch (record.get(2)){
                case "2018/2/3" : num = 7;break;
                case "2018/2/6" : num = 4;break;
                case "2018/2/7" : num = 3;break;
                default: num = 0;
            }
            for(int i = num;i > 0; i--){
                spBuilder.addPickupTimeWindow(86400 * (7 - i),21600 + 86400 * (7 - i));
                spBuilder.addDeliveryTimeWindow(86400 * (7 - i),21600 + 86400 * (7 - i));
            }
            vrpBuilder.addJob(spBuilder.build());
        }

        VehicleRoutingProblem problem = vrpBuilder.build();

		/*
         * get the algorithm out-of-the-box.
		 */
        StateManager stateManager = new StateManager(problem);
        ConstraintManager constraintManager = new ConstraintManager(problem, stateManager);
        constraintManager.addConstraint(new TaiPingConstraint(costMatrix), ConstraintManager.Priority.CRITICAL);
        VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(problem)
            .setStateAndConstraintManager(stateManager, constraintManager)
            .setProperty(Jsprit.Parameter.THREADS, "4")
            .buildAlgorithm();
        algorithm.setMaxIterations(2000);
        UnassignedJobReasonTracker unassignedJobReasonTracker = new UnassignedJobReasonTracker();
        algorithm.addListener(unassignedJobReasonTracker);

		/*
         * and search a solution
		 */
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();

		/*
         * get the best
		 */
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.VERBOSE);

		/*
         * plot
		 */

        new Plotter(problem, bestSolution).plot("output/taiping.png", "solution");

        for (Job job : bestSolution.getUnassignedJobs()) {
            System.out.println(unassignedJobReasonTracker.getMostLikelyReason(job.getId()));
        }

        //new GraphStreamViewer(problem, bestSolution).labelWith(GraphStreamViewer.Label.JOB_NAME).display();

    }

    public static List<List<String>> read(String fileName){
        List<List<String>> result = new ArrayList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("input/" + fileName));
            String line = "";
            boolean isStartLine = true;
            while ((line = br.readLine()) != null) {
                String[] content = line.split(",",-1);
                if (isStartLine) {
                    isStartLine = false;
                    continue;
                }
                List<String> order = new ArrayList<>();
                Collections.addAll(order, content);
                result.add(order);
            }
        }catch(IOException e){

        }finally{
            if(br != null){
                try {
                    br.close();
                    br = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public static void createInputFolder() {
        File dir = new File("input");
        // if the directory does not exist, create it
        if (!dir.exists()) {
            System.out.println("creating directory ./input");
            boolean result = dir.mkdir();
            if (result) System.out.println("./input created");
        }
    }
}
