package com.graphhopper.jsprit.core.problem.constraint;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.activity.DeliveryActivity;
import com.graphhopper.jsprit.core.problem.solution.route.activity.End;
import com.graphhopper.jsprit.core.problem.solution.route.activity.PickupActivity;
import com.graphhopper.jsprit.core.problem.solution.route.activity.Start;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;

/**
 * @author huangrun (huangrun@shanshu.ai)
 * @date 2018/03/02
 */
public class TaiPingConstraint implements HardActivityConstraint {

    private VehicleRoutingTransportCosts matrix;

    public TaiPingConstraint(VehicleRoutingTransportCosts matrix){
        this.matrix = matrix;
    }

    /**
     * 1.约束相同group或者距离很近才能放在一起
     *
     * 2.约束一条路线必须在相同的时间窗内
     *
     */
    @Override
    public ConstraintsStatus fulfilled(JobInsertionContext iFacts, TourActivity prevAct, TourActivity newAct,
                                       TourActivity nextAct, double prevActDepTime) {

        // 新的route随便放，没有约束
        // prevAct  newAct  nextAct
        // Start    任意     End
        if(prevAct instanceof Start && nextAct instanceof End){
            return ConstraintsStatus.FULFILLED;
        }

        // prevAct  newAct  nextAct
        // Start    待检查   之前插入的Act
        if(prevAct instanceof Start){
            if(!checkTW(nextAct, newAct) || !checkGroup(nextAct, newAct)){
               return ConstraintsStatus.NOT_FULFILLED;
            }
        }

        // prevAct      newAct  nextAct
        // 之前插入的Act  待检查   End
        else if (nextAct instanceof End) {
            if(!checkTW(prevAct, newAct) || !checkGroup(prevAct, newAct)){
                return ConstraintsStatus.NOT_FULFILLED;
            }
        }

        // prevAct      newAct  nextAct
        // 之前插入的Act  待检查   之前插入的Act
        else{
            if((!checkTW(prevAct, newAct) || !checkGroup(prevAct, newAct)) && !checkTW(nextAct, newAct) || !checkGroup(nextAct, newAct)){
                return ConstraintsStatus.NOT_FULFILLED;
            }
        }


        return ConstraintsStatus.FULFILLED;
    }

    private boolean checkTW(TourActivity existedAct, TourActivity newAct){
        double startTime = existedAct.getTheoreticalEarliestOperationStartTime();
        double endTime = existedAct.getTheoreticalLatestOperationStartTime();

        double newActStartTime = newAct.getTheoreticalEarliestOperationStartTime();
        double newActEndTime = newAct.getTheoreticalLatestOperationStartTime();
        if( newActStartTime >= startTime && newActEndTime <= endTime){
            return true;
        }else {
            return false;
        }
    }

    private boolean checkGroup(TourActivity existedAct, TourActivity newAct){
        int newGroupId = -1;
        int existedGroupId = -2;
        if(newAct instanceof TourActivity.JobActivity){
            Job job = ((TourActivity.JobActivity) newAct).getJob();
            if(job instanceof Shipment){
                newGroupId = ((Shipment) job).getTaipingGroup();
            }
        }

        if(existedAct instanceof TourActivity.JobActivity){
            Job job = ((TourActivity.JobActivity) existedAct).getJob();
            if(job instanceof Shipment){
                existedGroupId = ((Shipment) job).getTaipingGroup();
            }
        }

        if(newGroupId != existedGroupId){
            if(matrix.getDistance(existedAct.getLocation(), newAct.getLocation(), 0.0, null) < 1000){
                return true;
            }
            return false;
        }else {
            return true;
        }
    }
}
