/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ebrightmoon.zxing;

import com.google.zxing.ResultPoint;
import com.ebrightmoon.zxing.camera.CameraManager;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

  private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
  private static final long ANIMATION_DELAY = 80L;
  private static final int CURRENT_POINT_OPACITY = 0xA0;
  private static final int MAX_RESULT_POINTS = 20;
  private static final int POINT_SIZE = 6;

  private CameraManager cameraManager;
  private final Paint paint;
  private Bitmap resultBitmap;
  private final int maskColor;
  private final int resultColor;
  private final int laserColor;
  private final int resultPointColor;
  private int scannerAlpha;
  private List<ResultPoint> possibleResultPoints;
  private List<ResultPoint> lastPossibleResultPoints;

  // 设置宽高
  private int width;
  private int height;
  public static int FRAME_WIDTH = -1;
  public static int FRAME_HEIGHT = -1;
  public static int FRAME_MARGINTOP = -1;
  // 扫描框边角颜色
  private int innercornercolor;
  // 扫描框边角长度
  private int innercornerlength;
  // 扫描框边角宽度
  private int innercornerwidth;
  // 扫描线移动的y
  private int scanLineTop;
  // 扫描线移动速度
  private int SCAN_VELOCITY;
  // 扫描线
  private Bitmap scanLight;
  // 是否展示小圆点
  private boolean isCircle;
  private static final int OPAQUE = 0xFF;

  // This constructor is used when the class is built from an XML resource.
  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // Initialize these once for performance rather than calling them every time in onDraw().
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
//    maskColor = resources.getColor(R.color.viewfinder_mask);
//    resultColor = resources.getColor(R.color.result_view);
//    laserColor = resources.getColor(R.color.viewfinder_laser);
//    resultPointColor = resources.getColor(R.color.possible_result_points);

    TypedArray attributes = getContext().obtainStyledAttributes(attrs, R.styleable.ViewfinderView);

    this.maskColor = attributes.getColor(R.styleable.ViewfinderView_zxing_viewfinder_mask,
            resources.getColor(R.color.zxing_viewfinder_mask));
    this.resultColor = attributes.getColor(R.styleable.ViewfinderView_zxing_result_view,
            resources.getColor(R.color.zxing_result_view));
    this.laserColor = attributes.getColor(R.styleable.ViewfinderView_zxing_viewfinder_laser,
            resources.getColor(R.color.zxing_viewfinder_laser));
    this.resultPointColor = attributes.getColor(R.styleable.ViewfinderView_zxing_possible_result_points,
            resources.getColor(R.color.zxing_possible_result_points));


    //-----------------------------  边框 -----------------------------------

    // 扫描框距离顶部
    float innerMarginTop = attributes.getDimension(R.styleable.ViewfinderView_inner_margintop, -1);
    if (innerMarginTop != -1) {
   FRAME_MARGINTOP = (int) innerMarginTop;
    }

    // 扫描框的宽度
   FRAME_WIDTH = (int) attributes.getDimension(R.styleable.ViewfinderView_inner_width, DisplayUtil.screenWidthPx / 2);

    // 扫描框的高度
    FRAME_HEIGHT = (int) attributes.getDimension(R.styleable.ViewfinderView_inner_height, DisplayUtil.screenWidthPx / 2);

    // 扫描框边角颜色
    innercornercolor = attributes.getColor(R.styleable.ViewfinderView_inner_corner_color, Color.parseColor("#45DDDD"));
    // 扫描框边角长度
    innercornerlength = (int) attributes.getDimension(R.styleable.ViewfinderView_inner_corner_length, 65);
    // 扫描框边角宽度
    innercornerwidth = (int) attributes.getDimension(R.styleable.ViewfinderView_inner_corner_width, 15);

    // 扫描bitmap
    Drawable drawable = attributes.getDrawable(R.styleable.ViewfinderView_inner_scan_bitmap);
    if (drawable != null) {
    }

    // 扫描控件
    scanLight = BitmapFactory.decodeResource(getResources(), attributes.getResourceId(R.styleable.ViewfinderView_inner_scan_bitmap, R.drawable.scan_light));
    // 扫描速度
    SCAN_VELOCITY = attributes.getInt(R.styleable.ViewfinderView_inner_scan_speed, 5);

    isCircle = attributes.getBoolean(R.styleable.ViewfinderView_inner_scan_iscircle, true);

    attributes.recycle();
    scannerAlpha = 0;
    possibleResultPoints = new ArrayList<>(5);
    lastPossibleResultPoints = null;
  }

  public void setCameraManager(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }

//  @SuppressLint("DrawAllocation")
//  @Override
//  public void onDraw(Canvas canvas) {
//    if (cameraManager == null) {
//      return; // not ready yet, early draw before done configuring
//    }
//    Rect frame = cameraManager.getFramingRect();
//    Rect previewFrame = cameraManager.getFramingRectInPreview();
//    if (frame == null || previewFrame == null) {
//      return;
//    }
//    int width = canvas.getWidth();
//    int height = canvas.getHeight();
//
//    // Draw the exterior (i.e. outside the framing rect) darkened
//    paint.setColor(resultBitmap != null ? resultColor : maskColor);
//    canvas.drawRect(0, 0, width, frame.top, paint);
//    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
//    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
//    canvas.drawRect(0, frame.bottom + 1, width, height, paint);
//
//    if (resultBitmap != null) {
//      // Draw the opaque result bitmap over the scanning rectangle
//      paint.setAlpha(CURRENT_POINT_OPACITY);
//      canvas.drawBitmap(resultBitmap, null, frame, paint);
//    } else {
//      drawFrameBounds(canvas, frame);
//
//      drawScanLight(canvas, frame);
//      // Draw a red "laser scanner" line through the middle to show decoding is active
//      paint.setColor(laserColor);
//      paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
//      scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
//      int middle = frame.height() / 2 + frame.top;
//      canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);
//
//      float scaleX = frame.width() / (float) previewFrame.width();
//      float scaleY = frame.height() / (float) previewFrame.height();
//
//      List<ResultPoint> currentPossible = possibleResultPoints;
//      List<ResultPoint> currentLast = lastPossibleResultPoints;
//      int frameLeft = frame.left;
//      int frameTop = frame.top;
//      if (currentPossible.isEmpty()) {
//        lastPossibleResultPoints = null;
//      } else {
//        possibleResultPoints = new ArrayList<>(5);
//        lastPossibleResultPoints = currentPossible;
//        paint.setAlpha(CURRENT_POINT_OPACITY);
//        paint.setColor(resultPointColor);
//        synchronized (currentPossible) {
//          for (ResultPoint point : currentPossible) {
//            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
//                              frameTop + (int) (point.getY() * scaleY),
//                              POINT_SIZE, paint);
//          }
//        }
//      }
//      if (currentLast != null) {
//        paint.setAlpha(CURRENT_POINT_OPACITY / 2);
//        paint.setColor(resultPointColor);
//        synchronized (currentLast) {
//          float radius = POINT_SIZE / 2.0f;
//          for (ResultPoint point : currentLast) {
//            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
//                              frameTop + (int) (point.getY() * scaleY),
//                              radius, paint);
//          }
//        }
//      }
//
//      // Request another update at the animation interval, but only repaint the laser line,
//      // not the entire viewfinder mask.
//      postInvalidateDelayed(ANIMATION_DELAY,
//                            frame.left - POINT_SIZE,
//                            frame.top - POINT_SIZE,
//                            frame.right + POINT_SIZE,
//                            frame.bottom + POINT_SIZE);
//    }
//  }
  @Override
  public void onDraw(Canvas canvas) {
    if (cameraManager == null) {
      return; // not ready yet, early draw before done configuring
    }
    Rect frame = cameraManager.getFramingRect();
    Rect previewFrame = cameraManager.getFramingRectInPreview();
    if (frame == null || previewFrame == null) {
      return;
    }
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    // Draw the exterior (i.e. outside the framing rect) darkened
    paint.setColor(resultBitmap != null ? resultColor : maskColor);
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);

    if (resultBitmap != null) {
      // Draw the opaque result bitmap over the scanning rectangle
      paint.setAlpha(OPAQUE);
      canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
    } else {

      drawFrameBounds(canvas, frame);

      drawScanLight(canvas, frame);

      List<ResultPoint> currentPossible = possibleResultPoints;
      List<ResultPoint> currentLast = lastPossibleResultPoints;
      if (currentPossible.isEmpty()) {
        lastPossibleResultPoints = null;
      } else {
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = currentPossible;
        paint.setAlpha(OPAQUE);
        paint.setColor(resultPointColor);

        if (isCircle) {
          for (ResultPoint point : currentPossible) {
            canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, paint);
          }
        }
      }
      if (currentLast != null) {
        paint.setAlpha(OPAQUE / 2);
        paint.setColor(resultPointColor);

        if (isCircle) {
          for (ResultPoint point : currentLast) {
            canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, paint);
          }
        }
      }

      postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
    }
  }

  /**
   * 绘制取景框边框
   *
   * @param canvas
   * @param frame
   */
  private void drawFrameBounds(Canvas canvas, Rect frame) {

        /*paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);

        canvas.drawRect(frame, paint);*/

    paint.setColor(innercornercolor);
    paint.setStyle(Paint.Style.FILL);

    int corWidth = innercornerwidth;
    int corLength = innercornerlength;

    // 左上角
    canvas.drawRect(frame.left, frame.top, frame.left + corWidth, frame.top
            + corLength, paint);
    canvas.drawRect(frame.left, frame.top, frame.left
            + corLength, frame.top + corWidth, paint);
    // 右上角
    canvas.drawRect(frame.right - corWidth, frame.top, frame.right,
            frame.top + corLength, paint);
    canvas.drawRect(frame.right - corLength, frame.top,
            frame.right, frame.top + corWidth, paint);
    // 左下角
    canvas.drawRect(frame.left, frame.bottom - corLength,
            frame.left + corWidth, frame.bottom, paint);
    canvas.drawRect(frame.left, frame.bottom - corWidth, frame.left
            + corLength, frame.bottom, paint);
    // 右下角
    canvas.drawRect(frame.right - corWidth, frame.bottom - corLength,
            frame.right, frame.bottom, paint);
    canvas.drawRect(frame.right - corLength, frame.bottom - corWidth,
            frame.right, frame.bottom, paint);
  }


  /**
   * 绘制移动扫描线
   *
   * @param canvas
   * @param frame
   */
  private void drawScanLight(Canvas canvas, Rect frame) {

    if (scanLineTop == 0) {
      scanLineTop = frame.top;
    }

    if (scanLineTop >= frame.bottom - 30) {
      scanLineTop = frame.top;
    } else {
      scanLineTop += SCAN_VELOCITY;
    }
    Rect scanRect = new Rect(frame.left, scanLineTop, frame.right,
            scanLineTop + 30);
    canvas.drawBitmap(scanLight, null, scanRect, paint);
  }


  public void drawViewfinder() {
    Bitmap resultBitmap = this.resultBitmap;
    this.resultBitmap = null;
    if (resultBitmap != null) {
      resultBitmap.recycle();
    }
    invalidate();
  }

  /**
   * Draw a bitmap with the result points highlighted instead of the live scanning display.
   *
   * @param barcode An image of the decoded barcode.
   */
  public void drawResultBitmap(Bitmap barcode) {
    resultBitmap = barcode;
    invalidate();
  }

  public void addPossibleResultPoint(ResultPoint point) {
    List<ResultPoint> points = possibleResultPoints;
    synchronized (points) {
      points.add(point);
      int size = points.size();
      if (size > MAX_RESULT_POINTS) {
        // trim it
        points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
      }
    }
  }

}
