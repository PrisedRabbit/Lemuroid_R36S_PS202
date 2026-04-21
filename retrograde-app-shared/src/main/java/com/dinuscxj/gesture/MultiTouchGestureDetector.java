package com.dinuscxj.gesture;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public class MultiTouchGestureDetector {
  public static final int MAX_ROTATION = 360;
  public static final float NO_SCALE = 1.0f;
  public static final float NO_ROTATE = 0.0f;
  public static final float NO_MOVE = 0.0f;

  private final OnMultiTouchGestureListener listener;

  private float currentFocusX;
  private float currentFocusY;
  private float previousFocusX;
  private float previousFocusY;
  private float currentSpan;
  private float previousSpan;
  private float currentRotation;
  private float previousRotation;
  private long currentTime;
  private long previousTime;
  private boolean inProgress;
  private float initialSpan;
  private final int spanSlop;
  private float initialFocusX;
  private float initialFocusY;
  private final int touchSlopSquare;

  public MultiTouchGestureDetector(Context context, OnMultiTouchGestureListener listener) {
    this.listener = listener;

    ViewConfiguration configuration = ViewConfiguration.get(context);
    int touchSlop = configuration.getScaledTouchSlop();
    touchSlopSquare = touchSlop * touchSlop;
    spanSlop = touchSlop * 2;
  }

  public boolean onTouchEvent(MotionEvent event) {
    currentTime = event.getEventTime();

    int action = event.getActionMasked();
    int count = event.getPointerCount();

    boolean touchComplete = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
    boolean touchStart = action == MotionEvent.ACTION_DOWN;

    if (touchStart || touchComplete) {
      if (inProgress) {
        listener.onEnd(this);
        inProgress = false;
      }

      if (touchComplete) {
        return true;
      }
    }

    boolean configChanged =
        action == MotionEvent.ACTION_DOWN
            || action == MotionEvent.ACTION_POINTER_UP
            || action == MotionEvent.ACTION_POINTER_DOWN;

    boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
    int skipIndex = pointerUp ? event.getActionIndex() : -1;
    int div = pointerUp ? count - 1 : count;

    float sumX = 0f;
    float sumY = 0f;

    for (int i = 0; i < count; i++) {
      if (skipIndex == i) {
        continue;
      }

      sumX += event.getX(i);
      sumY += event.getY(i);
    }

    float focusX = sumX / div;
    float focusY = sumY / div;

    float devSumX = 0f;
    float devSumY = 0f;
    for (int i = 0; i < count; i++) {
      if (skipIndex == i) {
        continue;
      }

      devSumX += Math.abs(event.getX(i) - focusX);
      devSumY += Math.abs(event.getY(i) - focusY);
    }

    float spanX = (devSumX / div) * 2f;
    float spanY = (devSumY / div) * 2f;
    float span = (float) Math.hypot(spanX, spanY);

    float rotation = 0f;
    outer:
    for (int i = 0; i < count; i++) {
      if (skipIndex == i) {
        continue;
      }

      for (int j = i + 1; j < count; j++) {
        if (skipIndex == j) {
          continue;
        }

        double deltaX = event.getX(i) - event.getX(j);
        double deltaY = event.getY(i) - event.getY(j);
        rotation += (Math.toDegrees(Math.atan2(deltaY, deltaX)) + MAX_ROTATION) % MAX_ROTATION;
        break outer;
      }
    }

    boolean wasInProgress = inProgress;
    if (inProgress && configChanged) {
      listener.onEnd(this);
      inProgress = false;
    }

    if (configChanged) {
      initialSpan = previousSpan = currentSpan = span;
      initialFocusX = previousFocusX = currentFocusX = focusX;
      initialFocusY = previousFocusY = currentFocusY = focusY;
      previousRotation = currentRotation = rotation;
    }

    if (!inProgress
        && (wasInProgress
            || Math.abs(span - initialSpan) > spanSlop
            || Math.pow(currentFocusX - initialFocusX, 2.0d)
                    + Math.pow(currentFocusY - initialFocusY, 2.0d)
                > touchSlopSquare)) {
      previousSpan = currentSpan = span;
      previousTime = currentTime;
      previousFocusX = currentFocusX = focusX;
      previousFocusY = currentFocusY = focusY;
      previousRotation = currentRotation = rotation;
      inProgress = listener.onBegin(this);
    }

    if (action == MotionEvent.ACTION_MOVE) {
      currentSpan = span;
      currentFocusX = focusX;
      currentFocusY = focusY;
      currentRotation = rotation;

      if (inProgress) {
        if (getScale() != NO_SCALE) {
          listener.onScale(this);
        }

        if (getRotation() != NO_ROTATE) {
          listener.onRotate(this);
        }

        if (getMoveX() != NO_MOVE || getMoveY() != NO_MOVE) {
          listener.onMove(this);
        }
      }

      previousSpan = currentSpan;
      previousFocusX = currentFocusX;
      previousFocusY = currentFocusY;
      previousRotation = currentRotation;
      previousTime = currentTime;
    }

    return true;
  }

  public boolean isInProgress() {
    return inProgress;
  }

  public float getFocusX() {
    return currentFocusX;
  }

  public float getFocusY() {
    return currentFocusY;
  }

  public float getMoveX() {
    return currentFocusX - previousFocusX;
  }

  public float getMoveY() {
    return currentFocusY - previousFocusY;
  }

  public float getRotation() {
    return currentRotation - previousRotation;
  }

  public float getScale() {
    return previousSpan > 0 ? currentSpan / previousSpan : 1f;
  }

  public long getTimeDelta() {
    return currentTime - previousTime;
  }

  public long getEventTime() {
    return currentTime;
  }

  public interface OnMultiTouchGestureListener {
    void onScale(MultiTouchGestureDetector detector);
    void onMove(MultiTouchGestureDetector detector);
    void onRotate(MultiTouchGestureDetector detector);
    boolean onBegin(MultiTouchGestureDetector detector);
    void onEnd(MultiTouchGestureDetector detector);
  }

  public static class SimpleOnMultiTouchGestureListener implements OnMultiTouchGestureListener {
    @Override
    public void onScale(MultiTouchGestureDetector detector) {}

    @Override
    public void onMove(MultiTouchGestureDetector detector) {}

    @Override
    public void onRotate(MultiTouchGestureDetector detector) {}

    @Override
    public boolean onBegin(MultiTouchGestureDetector detector) {
      return true;
    }

    @Override
    public void onEnd(MultiTouchGestureDetector detector) {}
  }
}
