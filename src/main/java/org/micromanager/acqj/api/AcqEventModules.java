package org.micromanager.acqj.api;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.acqj.internal.acqengj.affineTransformUtils;

/**
 * A utility class with multiple "modules" functions for creating common
 * acquisition functions that can be combined to encode complex behaviors
 *
 * @author henrypinkard
 */
public class AcqEventModules {

   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack(int startSliceIndex, int stopSliceIndex, double zStep, double zOrigin) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            private int zIndex_ = startSliceIndex;

            @Override
            public boolean hasNext() {
               return zIndex_ < stopSliceIndex;
            }

            @Override
            public AcquisitionEvent next() {
               double zPos = zIndex_ * zStep + zOrigin;
               AcquisitionEvent sliceEvent = event.copy();
               //Do plus equals here in case z positions have been modified by another function (e.g. channel specific focal offsets)
               sliceEvent.setZ(zIndex_, 
                       (sliceEvent.getZPosition() == null ? 0.0 : sliceEvent.getZPosition()) + zPos);
               zIndex_++;
               return sliceEvent;
            }
         };
      };
   }

   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse(int numTimePoints, double interval_ms) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {

            int frameIndex_ = 0;
            private long lastTPEventStartTime_ = -1;

            @Override
            public boolean hasNext() {
               if (frameIndex_ == 0) {
                  return true;
               }
               if (frameIndex_ < numTimePoints) {
                  return true;
               }
               return false;
            }

            @Override
            public AcquisitionEvent next() {
               AcquisitionEvent timePointEvent = event.copy();

               timePointEvent.setMinimumStartTime( interval_ms == 0 ? 0 : lastTPEventStartTime_ + (long) interval_ms);
               timePointEvent.setTimeIndex(frameIndex_);
               if (frameIndex_ == 0) {
                  lastTPEventStartTime_ = System.currentTimeMillis();
               } else {
                  lastTPEventStartTime_ = timePointEvent.getMinimumStartTime();
               }
               frameIndex_++;

               return timePointEvent;
            }
         };
      };
   }

   /**
    * Make an iterator for events for each active channel
    *
    * @param channels
    * @return
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels(ChannelGroupSettings channels) {
      return (AcquisitionEvent event) -> {
         return new Iterator<AcquisitionEvent>() {
            String channelName_ = null;

            @Override
            public boolean hasNext() {
               if (channels.nextActiveChannel(channelName_) != null) {
                  return true;
               }
               return false;
            }

            @Override
            public AcquisitionEvent next() {
               AcquisitionEvent channelEvent = event.copy();
               channelName_ = channels.nextActiveChannel(channelName_);
               channelEvent.setChannelName(channelName_);
               channelEvent.setZ(channelEvent.getZIndex(), channelEvent.getZPosition()
                       + channels.getChannelSetting(channelName_).offset_);
               return channelEvent;
            }
         };
      };
   }

   /**
    * Iterate over an arbitrary list of positions
    * @param positions
    * @return
    */
   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(List<XYStagePosition> positions) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         if (positions == null) {
            builder.accept(event);
         } else {
            for (int index = 0; index < positions.size(); index++) {
               AcquisitionEvent posEvent = event.copy();
               posEvent.setXY( positions.get(index));
               builder.accept(posEvent);
            }
         }
         return builder.build().iterator();
      };
   }
   
   /**
    * Make a list of XY positions corresponding to a tiled region of XY space, 
    * based on the affine transform relating pixel and stage coordinates for the 
    * current xy stage
    * @param tileOverlapPercent
    * @return 
    */
    public static ArrayList<XYStagePosition> tileXYPositions(double tileOverlapPercent, 
            double centerX, double centerY, int numRows, int numCols) {
      try {
         AffineTransform transform = affineTransformUtils.getAffineTransform(centerX, centerY);
         ArrayList<XYStagePosition> positions = new ArrayList<XYStagePosition>();
         int fullTileWidth = (int) Engine.getCore().getImageWidth();
         int fullTileHeight = (int) Engine.getCore().getImageHeight();
         int overlapX = (int) (Engine.getCore().getImageWidth() * tileOverlapPercent);
         int overlapY = (int) (Engine.getCore().getImageHeight() * tileOverlapPercent);
         int tileWidthMinusOverlap = fullTileWidth - overlapX;
         int tileHeightMinusOverlap = fullTileHeight - overlapY;
         for (int col = 0; col < numCols; col++) {
            double xPixelOffset = (col - (numCols - 1) / 2.0) * tileWidthMinusOverlap;
            //add in snaky behavior
                if (col % 2 == 0) {
                    for (int row = 0; row < numRows; row++) {
                        double yPixelOffset = (row - (numRows - 1) / 2.0) * tileHeightMinusOverlap;
                        Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                        Point2D.Double stagePos = new Point2D.Double();
                        transform.transform(pixelPos, stagePos);
                        AffineTransform posTransform = affineTransformUtils.getAffineTransform(stagePos.x, stagePos.y);
                        positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                                fullTileWidth, fullTileHeight, row, col, posTransform));
                    }
                } else {  
                    for (int row = numRows - 1; row >= 0; row--) {
                        double yPixelOffset = (row - (numRows - 1) / 2.0) * tileHeightMinusOverlap;
                        Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
                        Point2D.Double stagePos = new Point2D.Double();
                        transform.transform(pixelPos, stagePos);
                        AffineTransform posTransform = affineTransformUtils.getAffineTransform( stagePos.x, stagePos.y);
                        positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                                fullTileWidth, fullTileHeight, row, col, posTransform));
                    }
                }
         }    
         return positions;
      } catch (Exception ex) {
         throw new RuntimeException("Couldn't get affine transform");
      }
   }

}
