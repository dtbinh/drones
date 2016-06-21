package environment.target;

import java.awt.Color;
import java.util.ArrayList;

import commoninterface.AquaticDroneCI;
import commoninterface.entities.Entity;
import commoninterface.entities.target.Formation;
import commoninterface.entities.target.Formation.FormationType;
import commoninterface.entities.target.Target;
import commoninterface.entities.target.motion.LinearMotionData;
import commoninterface.entities.target.motion.RotationMotionData;
import commoninterface.mathutils.Vector2d;
import commoninterface.utils.CoordinateUtilities;
import net.jafama.FastMath;
import simulation.Simulator;
import simulation.physicalobjects.LightPole;
import simulation.robot.AquaticDrone;
import simulation.robot.Robot;
import simulation.util.Arguments;

public class FormationMultiTargetEnvironment extends TargetEnvironment {
	private static final long serialVersionUID = -6746690210418679841L;

	private int targetsQuantity = 1;
	private String formationShape = null;
	private boolean variateTargetsQuantity = false;
	private boolean variateFormationParameters = false;
	private boolean onePerOneRobotTarget = false;

	private boolean injectFaults = false;
	private boolean varyFaultDuration = true;
	private AquaticDrone faultyRobot = null;
	private int faultDuration = 500;
	private int timestepToInjectFaults;
	private double initialRotationAngle = 0;

	private double rotationVelocity = 0.05;
	private boolean rotateFormation = false;
	private boolean rotationDirection = false;
	private boolean variateRotationVelocity = false;
	private boolean variateRotationDirection = false;

	private double lineFormationDelta = 2.0;
	private double circleFormation_radius = 7.5;
	private double arrowFormation_xDelta = lineFormationDelta;
	private double arrowFormation_yDelta = lineFormationDelta;

	private Formation formation = null;

	public FormationMultiTargetEnvironment(Simulator simulator, Arguments args) {
		super(simulator, args);
		targetsQuantity = args.getArgumentAsIntOrSetDefault("targetsQuantity", targetsQuantity);
		variateTargetsQuantity = args.getArgumentAsIntOrSetDefault("variateTargetsQnt", 0) == 1;
		onePerOneRobotTarget = args.getArgumentAsIntOrSetDefault("onePerOneRobotTarget", 0) == 1;

		rotateFormation = args.getArgumentAsIntOrSetDefault("rotateFormation", 0) == 1;
		rotationVelocity = args.getArgumentAsDoubleOrSetDefault("rotationVelocity", rotationVelocity);
		rotationDirection = args.getArgumentAsIntOrSetDefault("rotationDirection", 0) == 1;
		variateRotationVelocity = args.getArgumentAsIntOrSetDefault("variateRotationVelocity", 0) == 1;
		variateRotationDirection = args.getArgumentAsIntOrSetDefault("variateRotationDirection", 0) == 1;

		formationShape = args.getArgumentAsStringOrSetDefault("formationShape", null);
		variateFormationParameters = args.getArgumentAsIntOrSetDefault("variateFormationParameters", 0) == 1;

		if (args.getArgumentIsDefined("initialFormationRotation")) {
			initialRotationAngle = args.getArgumentAsDouble("initialFormationRotation") * Math.PI / 180;
		}

		lineFormationDelta = args.getArgumentAsDoubleOrSetDefault("lineFormation_xDelta", lineFormationDelta);
		circleFormation_radius = args.getArgumentAsDoubleOrSetDefault("circleFormation_radius", circleFormation_radius);
		arrowFormation_xDelta = args.getArgumentAsDoubleOrSetDefault("arrowFormation_xDelta", arrowFormation_xDelta);
		arrowFormation_yDelta = args.getArgumentAsDoubleOrSetDefault("arrowFormation_yDelta", arrowFormation_yDelta);
		injectFaults = args.getArgumentAsIntOrSetDefault("injectFaults", 0) == 1;
		faultDuration = args.getArgumentAsIntOrSetDefault("faultDuration", faultDuration);
	}

	@Override
	public void setup(Simulator simulator) {
		super.setup(simulator);

		LightPole center = new LightPole(simulator, "center", 0, 0, 1);
		center.setColor(Color.MAGENTA);
		addMarker(center);

		if (onePerOneRobotTarget) {
			targetsQuantity = robots.size();
		}

		if (variateTargetsQuantity) {
			targetsQuantity += targetsQuantity * simulator.getRandom().nextDouble() * 3;
			if (targetsQuantity == 0) {
				targetsQuantity = 1;
			}
		}

		if (targetsQuantity <= 0) {
			throw new IllegalArgumentException(
					"Targets quantity needs to be equal or greater than 1 (currently " + targetsQuantity + ")");
		}

		FormationType formationType = FormationType.valueOf(formationShape);

		// If we are in mix mode, just pick one of the formations to use
		if (formationType == FormationType.mix) {
			int bound = FormationType.values().length;
			int position = simulator.getRandom().nextInt(bound - 1);
			formationType = FormationType.values()[position];
		}
		generateFormation(formationType);
		generateFormationMotionData();

		for (Robot r : robots) {
			positionDroneInRandomPos((AquaticDrone) r, simulator);

			if (r instanceof AquaticDroneCI) {
				((AquaticDroneCI) r).getEntities().add(formation);
				((AquaticDroneCI) r).setUpdateEntities(true);
			}
		}

		if (injectFaults) {
			if (varyFaultDuration) {
				// If faultDuration=500, the varied fault duration is in
				// [250,750] range
				faultDuration += (simulator.getRandom().nextDouble() - 0.5) * faultDuration;
			}

			timestepToInjectFaults = (int) (simulator.getRandom().nextDouble() * (steps - faultDuration));

			boolean choosed = false;
			while (!choosed) {
				int position = simulator.getRandom().nextInt(robots.size());

				if (robots.get(position) instanceof AquaticDrone) {
					faultyRobot = (AquaticDrone) robots.get(position);
					choosed = true;
				}
			}
		}

		setup = true;
	}

	public static ArrayList<Entity> cloneEntities(ArrayList<Entity> list) {
		ArrayList<Entity> clone = new ArrayList<Entity>(list.size());
		for (Entity item : list) {
			clone.add(item.clone());
		}
		return clone;
	}

	private void generateFormation(FormationType formationType) {
		double radius = radiusOfObjPositioning * simulator.getRandom().nextDouble();
		double orientation = (simulator.getRandom().nextDouble() * FastMath.PI * 2) % (FastMath.PI * 2);

		Vector2d position = new Vector2d(radius * FastMath.cos(orientation), radius * FastMath.sin(orientation));

		double initialRotationAngle = 0;
		if (!args.getArgumentIsDefined("initialFormationRotation")) {
			initialRotationAngle = simulator.getRandom().nextDouble() * 2 * FastMath.PI;
		} else {
			initialRotationAngle = this.initialRotationAngle;
		}

		formation = new Formation("formation", CoordinateUtilities.cartesianToGPS(position));
		formation.setLineFormationDelta(lineFormationDelta);
		formation.setArrowFormationDeltas(new Vector2d(arrowFormation_xDelta, arrowFormation_yDelta));
		formation.setCircleFormationRadius(circleFormation_radius);
		formation.setVariateFormationParameters(variateFormationParameters);
		formation.setInitialRotation(initialRotationAngle);
		formation.setRandomSeed(simulator.getRandomSeed());
		formation.setSafetyDistance(safetyDistance);
		formation.setRadiusOfObjPositioning(radiusOfObjPositioning);
		formation.buildFormation(targetsQuantity, formationType, targetRadius);

		targets.addAll(formation.getTargets());
	}

	private void generateFormationMotionData() {
		double targetsVelocity = movementVelocity;
		if (variateTargetVelocity) {
			targetsVelocity = 0.1 * movementVelocity + simulator.getRandom().nextDouble() * movementVelocity * 2;
		}

		double targetsAzimuth = movementAzimuth;
		if (variateTargetAzimuth) {
			targetsAzimuth = simulator.getRandom().nextDouble() * Math.PI * 2;
		}

		double angularVelocity = rotationVelocity;
		if (variateRotationVelocity) {
			angularVelocity = 0.1 * rotationVelocity + simulator.getRandom().nextDouble() * rotationVelocity * 1.5;
		}

		boolean direction = rotationDirection;
		if (variateRotationDirection) {
			direction = simulator.getRandom().nextBoolean();
		}

		if (rotateFormation) {
			for (Target t : formation.getTargets()) {

				RotationMotionData rmd = new RotationMotionData(t, formation.getLatLon(), angularVelocity, direction);
				t.setMotionData(rmd);
			}
		}

		if (moveTargets) {
			LinearMotionData lmd = new LinearMotionData(formation, targetsVelocity, targetsAzimuth);
			formation.setMotionData(lmd);

			// RotationMotionData rmd = new RotationMotionData(formation, new
			// Vector2d(0, 0), angularVelocity, direction);
			// formation.setMotionData(rmd);
		}
	}

	@Override
	public void update(double time) {
		// formation.step(time);

		if (injectFaults) {
			// Time to recover from fault?
			if (time > timestepToInjectFaults + faultDuration) {
				faultyRobot.setFault(false);
			} else {
				if (simulator.getTime() >= timestepToInjectFaults) {
					faultyRobot.setFault(true);
				}
			}
		}
	}

}
