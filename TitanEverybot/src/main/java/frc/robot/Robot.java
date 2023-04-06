// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;

import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class Robot extends TimedRobot {
  /*
   * Autonomous selection options.
   */
  private static final String kNothingAuto = "do nothing";
  private static final String kConeMobilityAuto = "cone and mobility";
  private static final String kConeAuto = "cone";
  private static final String kDelayConeMobilityAuto = "delay cone and mobility";
  private String autoSelected;
  private final SendableChooser<String> chooser = new SendableChooser<>();

  /*
   * Drive motor controller instances.
   * 
   * Change the id's to match your robot.
   * Change kBrushed to kBrushless if you are using NEO's.
   * Use the appropriate other class if you are using different controllers.
   */
  WPI_TalonSRX driveLeftMaster = new WPI_TalonSRX(3);
  WPI_TalonSRX driveRightMaster = new WPI_TalonSRX(1);
  WPI_TalonSRX driveLeftFollower = new WPI_TalonSRX(4);
  WPI_TalonSRX driveRightFollower = new WPI_TalonSRX(2);

  /*
   * Mechanism motor controller instances.
   * 
   * Like the drive motors, set the CAN id's to match your robot or use different
   * motor controller classes (TalonFX, TalonSRX, Spark, VictorSP) to match your
   * robot.
   * 
   * The arm is a NEO on Everybot.
   * The intake is a NEO 550 on Everybot.
   */
  CANSparkMax arm = new CANSparkMax(5, MotorType.kBrushless);
  CANSparkMax intake = new CANSparkMax(6, MotorType.kBrushless);

  DifferentialDrive drive;

  /**
   * The starter code uses the most generic joystick class.
   * 
   * The reveal video was filmed using a logitech gamepad set to
   * direct input mode (switch set to D on the bottom). You may want
   * to use the XBoxController class with the gamepad set to XInput
   * mode (switch set to X on the bottom) or a different controller
   * that you feel is more comfortable.
   */
  Joystick j = new Joystick(0);

  /*
   * Magic numbers. Use these to adjust settings.
   */

  /**
   * How many amps the arm motor can use.
   */
  static final int ARM_CURRENT_LIMIT_A = 20;

  /**
   * Percent output to run the arm up/down at
   */
  static final double ARM_OUTPUT_POWER = 0.4;

  /**
   * How many amps the intake can use while picking up
   */
  static final int INTAKE_CURRENT_LIMIT_A = 25;

  /**
   * How many amps the intake can use while holding
   */
  static final int INTAKE_HOLD_CURRENT_LIMIT_A = 5;

  /**
   * Percent output for intaking
   */
  static final double INTAKE_OUTPUT_POWER = 1.0;

  /**
   * Percent output for holding
   */
  static final double INTAKE_HOLD_POWER = 0.07;

  /**
   * Decides if bot leaves community in auto.
   */
  boolean mobility;
  /**
   * Auto constants
   */
  static final double AUTO_DRIVE_SPEED = 0.75;
  static final double AUTO_INTAKE_SPEED = 1.0;
  static final double AUTO_ARM_PIVOT_OUT_SPEED = 0.6;
  static final double AUTO_ARM_PIVOT_IN_SPEED = -0.3;
  /**
   * Auto timing constants
   * AT means Auto Timing
   * Variables with 'dur' at the end are the duration of the action,
   * Vars without 'dur' are the variables used in the timing and should not mbe tampered with unless changing the order of auto actions.
   */
  static final double AT_DELAY = 5.0;
  double AT_START_TIME = 0.0;
  // Arm out
  static final double AT_ARM_OUT_DUR = 1.1;
  double AT_ARM_OUT = AT_ARM_OUT_DUR;
  // Output
  static final double AT_INTAKE_DUR = 0.9;
  double AT_INTAKE = AT_ARM_OUT + AT_INTAKE_DUR;
  // Arm in
  static final double AT_ARM_IN_DUR = 1.0;
  double AT_ARM_IN = AT_INTAKE + AT_ARM_IN_DUR;
  // Drive
  static final double AT_DRIVE_DUR = 2.0;
  double AT_DRIVE = AT_ARM_IN + AT_DRIVE_DUR;

  /**
   * This method is run once when the robot is first started up.
   */
  @Override
  public void robotInit() {
    chooser.setDefaultOption("do nothing", kNothingAuto);
    chooser.addOption("cone only", kConeAuto);
    chooser.addOption("cone and mobility", kConeMobilityAuto);
    chooser.addOption("delay cone and mobility", kDelayConeMobilityAuto);
    SmartDashboard.putData("Auto choices", chooser);

    /*
     * You will need to change some of these from false to true.
     * 
     * In the setDriveMotors method, comment out all but 1 of the 4 calls
     * to the set() methods. Push the joystick forward. Reverse the motor
     * if it is going the wrong way. Repeat for the other 3 motors.
     */
    driveLeftMaster.setInverted(false);
    driveLeftFollower.setInverted(false);
    driveRightMaster.setInverted(true);
    driveRightFollower.setInverted(true);

    driveRightFollower.follow(driveRightMaster);
    driveLeftFollower.follow(driveLeftMaster);

    drive = new DifferentialDrive(driveLeftMaster, driveRightMaster);

    /*
     * Set the arm and intake to brake mode to help hold position.
     * If either one is reversed, change that here too. Arm out is defined
     * as positive, arm in is negative.
     */
    arm.setInverted(true);
    arm.setIdleMode(IdleMode.kBrake);
    arm.setSmartCurrentLimit(ARM_CURRENT_LIMIT_A);
    intake.setInverted(false);
    intake.setIdleMode(IdleMode.kBrake);
  }

  /**
   * Calculate and set the power to apply to the left and right
   * drive motors.
   * 
   * @param forward Desired forward speed. Positive is forward.
   * @param turn    Desired turning speed. Positive is counterclockwise from
   *                above.
   */
  public void setDriveMotors(double forward, double turn) {

    // see note above in robotInit about commenting these out one by one to set
    // directions.

    drive.arcadeDrive(forward, turn);
  }

  /**
   * Set the arm output power. Positive is out, negative is in.
   * 
   * @param percent
   */
  public void setArmMotor(double percent) {
    arm.set(percent);
    SmartDashboard.putNumber("arm power (%)", percent);
    SmartDashboard.putNumber("arm motor current (amps)", arm.getOutputCurrent());
    SmartDashboard.putNumber("arm motor temperature (C)", arm.getMotorTemperature());
  }

  /**
   * Set the arm output power.
   * 
   * @param percent desired speed
   * @param amps current limit
   */
  public void setIntakeMotor(double percent, int amps) {
    intake.set(percent);
    intake.setSmartCurrentLimit(amps);
    SmartDashboard.putNumber("intake power (%)", percent);
    SmartDashboard.putNumber("intake motor current (amps)", intake.getOutputCurrent());
    SmartDashboard.putNumber("intake motor temperature (C)", intake.getMotorTemperature());
  }

  /**
   * This method is called every 20 ms, no matter the mode. It runs after
   * the autonomous and teleop specific period methods.
   */
  @Override
  public void robotPeriodic() {
    SmartDashboard.putNumber("Time (seconds)", Timer.getFPGATimestamp());
  }

  double autonomousStartTime;
  double autonomousIntakePower;

  @Override
  public void autonomousInit() {
    driveLeftMaster.setNeutralMode(NeutralMode.Brake);
    driveLeftFollower.setNeutralMode(NeutralMode.Brake);
    driveRightMaster.setNeutralMode(NeutralMode.Brake);
    driveRightFollower.setNeutralMode(NeutralMode.Brake);

    autoSelected = chooser.getSelected();

    if (autoSelected != null && autoSelected.equals(kConeAuto))
    {
      mobility = false;
    }
    else if (autoSelected != null && autoSelected.equals(kDelayConeMobilityAuto)) {
      AT_START_TIME += AT_DELAY;
      AT_ARM_OUT += AT_DELAY;
      AT_INTAKE += AT_DELAY;
      AT_ARM_IN += AT_DELAY;
      AT_DRIVE += AT_DELAY;
      mobility = true;
    } else {
      mobility = true;
    }

    autonomousStartTime = Timer.getFPGATimestamp();
    SmartDashboard.putBoolean("Run auto", false);
  }

  @Override
  public void autonomousPeriodic() {
    /**
     * If do nothing auto is selected ignore the rest of auto period.
     */
    if (autoSelected != null && autoSelected.equals(kNothingAuto)) {
      setArmMotor(0.0);
      setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(0.0, 0.0);
      return;
    }

    SmartDashboard.putBoolean("Run auto", true);
    double timeElapsed = Timer.getFPGATimestamp() - autonomousStartTime;

    /**
     * Auto actions in following order,
     * - Pause for if delay auto is selected.
     * - Score cone on high row.
     * - Break auto if mobility isn't selected.
     * - Drive out of community.
     */
    if (timeElapsed < AT_START_TIME) {
      setArmMotor(0.0);
      setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(0.0, 0.0);
    }
    else if (timeElapsed < AT_ARM_OUT) {
      setArmMotor(AUTO_ARM_PIVOT_OUT_SPEED);
    }
    else if (timeElapsed < AT_INTAKE) {
      setArmMotor(0.0);
      setIntakeMotor(AUTO_INTAKE_SPEED, INTAKE_CURRENT_LIMIT_A);
    }
    else if(timeElapsed < AT_ARM_IN) {
      setIntakeMotor(0.0, INTAKE_HOLD_CURRENT_LIMIT_A);
      setArmMotor(AUTO_ARM_PIVOT_IN_SPEED);
    }
    else if(timeElapsed < AT_DRIVE && mobility) {
      setArmMotor(0.0);
      setDriveMotors(AUTO_DRIVE_SPEED, 0.0);
    }
    else {
      setArmMotor(0.0);
      setDriveMotors(0.0, 0.0);
    }
  }

  /**
   * Used to remember the last game piece picked up to apply some holding power.
   */
  static final int CONE = 1;
  static final int CUBE = 2;
  static final int NOTHING = 3;
  int lastGamePiece;

  @Override
  public void teleopInit() {
    driveLeftMaster.setNeutralMode(NeutralMode.Brake);
    driveLeftFollower.setNeutralMode(NeutralMode.Brake);
    driveRightMaster.setNeutralMode(NeutralMode.Brake);
    driveRightFollower.setNeutralMode(NeutralMode.Brake);

    lastGamePiece = NOTHING;
  }

  @Override
  public void teleopPeriodic() {
    double armPower;
    if (j.getRawButton(3)) {
      // lower the arm
      armPower = -ARM_OUTPUT_POWER;
    } else if (j.getRawButton(4)) {
      // raise the arm
      armPower = ARM_OUTPUT_POWER;
    } else {
      // do nothing and let it sit where it is
      armPower = 0.0;
    }
    setArmMotor(armPower);
  
    double intakePower;
    int intakeAmps;
    if (j.getRawButton(5)) {
      // cube in or cone out
      intakePower = INTAKE_OUTPUT_POWER;
      intakeAmps = INTAKE_CURRENT_LIMIT_A;
      lastGamePiece = CUBE;
    } else if (j.getRawButton(6)) {
      // cone in or cube out
      intakePower = -INTAKE_OUTPUT_POWER;
      intakeAmps = INTAKE_CURRENT_LIMIT_A;
      lastGamePiece = CONE;
    } else if (lastGamePiece == CUBE) {
      intakePower = INTAKE_HOLD_POWER;
      intakeAmps = INTAKE_HOLD_CURRENT_LIMIT_A;
    } else if (lastGamePiece == CONE) {
      intakePower = -INTAKE_HOLD_POWER;
      intakeAmps = INTAKE_HOLD_CURRENT_LIMIT_A;
    } else {
      intakePower = 0.0;
      intakeAmps = 0;
    }
    setIntakeMotor(intakePower, intakeAmps);

    /*
     * Negative signs here because the values from the analog sticks are backwards
     * from what we want. Forward returns a negative when we want it positive.
     */
    setDriveMotors(-j.getRawAxis(1), -j.getRawAxis(0));
  }
}