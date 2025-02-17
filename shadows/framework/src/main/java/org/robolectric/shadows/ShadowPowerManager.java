package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static com.google.common.base.Preconditions.checkState;
import static org.robolectric.shadows.ShadowApplication.getInstance;

import android.os.PowerManager;
import android.os.WorkSource;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.HiddenApi;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;
import org.robolectric.shadow.api.Shadow;

@Implements(PowerManager.class)
public class ShadowPowerManager {
  private boolean isScreenOn = true;
  private boolean isInteractive = true;
  private boolean isPowerSaveMode = false;
  private boolean isDeviceIdleMode = false;

  @PowerManager.LocationPowerSaveMode
  private int locationMode = PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF;

  private List<String> rebootReasons = new ArrayList<String>();
  private Map<String, Boolean> ignoringBatteryOptimizations = new HashMap<>();

  @Implementation
  protected PowerManager.WakeLock newWakeLock(int flags, String tag) {
    PowerManager.WakeLock wl = Shadow.newInstanceOf(PowerManager.WakeLock.class);
    ((ShadowWakeLock) Shadow.extract(wl)).setTag(tag);
    getInstance().addWakeLock(wl);
    return wl;
  }

  @Implementation
  protected boolean isScreenOn() {
    return isScreenOn;
  }

  public void setIsScreenOn(boolean screenOn) {
    isScreenOn = screenOn;
  }

  @Implementation(minSdk = LOLLIPOP)
  protected boolean isInteractive() {
    return isInteractive;
  }

  public void setIsInteractive(boolean interactive) {
    isInteractive = interactive;
  }

  @Implementation(minSdk = LOLLIPOP)
  protected boolean isPowerSaveMode() {
    return isPowerSaveMode;
  }

  public void setIsPowerSaveMode(boolean powerSaveMode) {
    isPowerSaveMode = powerSaveMode;
  }

  private Map<Integer, Boolean> supportedWakeLockLevels = new HashMap<>();

  @Implementation(minSdk = LOLLIPOP)
  protected boolean isWakeLockLevelSupported(int level) {
    return supportedWakeLockLevels.containsKey(level) ? supportedWakeLockLevels.get(level) : false;
  }

  public void setIsWakeLockLevelSupported(int level, boolean supported) {
    supportedWakeLockLevels.put(level, supported);
  }

  /**
   * @return `false` by default, or the value specified via {@link #setIsDeviceIdleMode(boolean)}
   */
  @Implementation(minSdk = M)
  protected boolean isDeviceIdleMode() {
    return isDeviceIdleMode;
  }

  /** Sets the value returned by {@link #isDeviceIdleMode()}. */
  public void setIsDeviceIdleMode(boolean isDeviceIdleMode) {
    this.isDeviceIdleMode = isDeviceIdleMode;
  }

  /**
   * Returns how location features should behave when battery saver is on. When battery saver is
   * off, this will always return {@link #LOCATION_MODE_NO_CHANGE}.
   */
  @Implementation(minSdk = P)
  @PowerManager.LocationPowerSaveMode
  protected int getLocationPowerSaveMode() {
    if (!isPowerSaveMode()) {
      return PowerManager.LOCATION_MODE_NO_CHANGE;
    }
    return locationMode;
  }

  /** Sets the value returned by {@link #getLocationPowerSaveMode()} when battery saver is on. */
  public void setLocationPowerSaveMode(@PowerManager.LocationPowerSaveMode int locationMode) {
    checkState(
        locationMode >= PowerManager.MIN_LOCATION_MODE,
        "Location Power Save Mode must be at least " + PowerManager.MIN_LOCATION_MODE);
    checkState(
        locationMode <= PowerManager.MAX_LOCATION_MODE,
        "Location Power Save Mode must be no more than " + PowerManager.MAX_LOCATION_MODE);
    this.locationMode = locationMode;
  }

  /** Discards the most recent {@code PowerManager.WakeLock}s */
  @Resetter
  public static void reset() {
    ShadowApplication shadowApplication = ShadowApplication.getInstance();
    if (shadowApplication != null) {
      shadowApplication.clearWakeLocks();
    }
  }

  /**
   * Retrieves the most recent wakelock registered by the application
   *
   * @return Most recent wake lock.
   */
  public static PowerManager.WakeLock getLatestWakeLock() {
    ShadowApplication shadowApplication = Shadow.extract(RuntimeEnvironment.application);
    return shadowApplication.getLatestWakeLock();
  }

  @Implementation(minSdk = M)
  protected boolean isIgnoringBatteryOptimizations(String packageName) {
    Boolean result = ignoringBatteryOptimizations.get(packageName);
    return result == null ? false : result;
  }

  public void setIgnoringBatteryOptimizations(String packageName, boolean value) {
    ignoringBatteryOptimizations.put(packageName, Boolean.valueOf(value));
  }

  @Implementation
  protected void reboot(String reason) {
    rebootReasons.add(reason);
  }

  /** Returns the number of times {@link #reboot(String)} was called. */
  public int getTimesRebooted() {
    return rebootReasons.size();
  }

  /** Returns the list of reasons for each reboot, in chronological order. */
  public ImmutableList<String> getRebootReasons() {
    return ImmutableList.copyOf(rebootReasons);
  }

  @Implements(PowerManager.WakeLock.class)
  public static class ShadowWakeLock {
    private boolean refCounted = true;
    private int refCount = 0;
    private boolean locked = false;
    private WorkSource workSource = null;
    private int timesHeld = 0;
    private String tag = null;

    @Implementation
    protected void acquire() {
      acquire(0);
    }

    @Implementation
    protected synchronized void acquire(long timeout) {
      ++timesHeld;
      if (refCounted) {
        refCount++;
      } else {
        locked = true;
      }
    }

    @Implementation
    protected synchronized void release() {
      if (refCounted) {
        if (--refCount < 0) throw new RuntimeException("WakeLock under-locked");
      } else {
        locked = false;
      }
    }

    @Implementation
    protected synchronized boolean isHeld() {
      return refCounted ? refCount > 0 : locked;
    }

    /**
     * Retrieves if the wake lock is reference counted or not
     *
     * @return Is the wake lock reference counted?
     */
    public boolean isReferenceCounted() {
      return refCounted;
    }

    @Implementation
    protected void setReferenceCounted(boolean value) {
      refCounted = value;
    }

    @Implementation
    protected synchronized void setWorkSource(WorkSource ws) {
      workSource = ws;
    }

    public synchronized WorkSource getWorkSource() {
      return workSource;
    }

    /** Returns how many times the wakelock was held. */
    public int getTimesHeld() {
      return timesHeld;
    }

    /** Returns the tag. */
    @HiddenApi
    @Implementation(minSdk = O)
    public String getTag() {
      return tag;
    }

    /** Sets the tag. */
    private void setTag(String tag) {
      this.tag = tag;
    }
  }
}
