package com.mediatek.batterywarning;

/**
 * @author mtk80968
 * 
 */
public class BatteryNotifyCodes {

    // battery is ok
    public static final int BATTERY_OK = 0x0000;
    // over charger voltage, please disconnect charger
    public static final int CHARGER_OVER_VOLTAGE = 0x0001;
    // over battery temperature, please remove battery
    public static final int BATTER_OVER_TEMPERATURE = 0x0002;
    // over current-protection, please disconnect charger
    public static final int OVER_CURRENT_PROTECTION = 0x0004;
    // over battery voltage, please remove battery
    public static final int BATTER_OVER_VOLTAGE = 0x0008;
    // over 12hours, battery does not charge full, please disconnect charger
    public static final int SAFETY_TIMER_TIMEOUT = 0x0010;

    /**
     * Broadcast Action: Charger is over voltage.
     */
    public static final String ACTION_CHARGER_OVER_VOLTAGE = "mediatek.intent.action.CHARGER_OVER_VOLTAGE";
    /**
     * Broadcast Action: Battery is over temperature.
     */
    public static final String ACTION_BATTER_OVER_TEMPERATURE = "mediatek.intent.action.BATTER_OVER_TEMPERATURE";
    /**
     * Broadcast Action: over current-protection situation.
     */
    public static final String ACTION_OVER_CURRENT_PROTECTION = "mediatek.intent.action.OVER_CURRENT_PROTECTION";
    /**
     * Broadcast Action: Battery is over voltage.
     */
    public static final String ACTION_BATTER_OVER_VOLTAGE = "mediatek.intent.action.BATTER_OVER_VOLTAGE";
    /**
     * Broadcast Action: Over 12hours, battery does not charge full.
     */
    public static final String ACTION_SAFETY_TIMER_TIMEOUT = "mediatek.intent.action.SAFETY_TIMER_TIMEOUT";
}
