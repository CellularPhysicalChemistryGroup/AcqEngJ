package org.micromanager.acqj.api;

import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.main.Acquisition;

/**
 * Where the acquisition sends data to. Conventionally would be saving + image display.
 * When using AcqEngJ, either a DataSink or a custom TaggedImageProcessor that intercepts
 * and diverts images rather than passing them along should be implemented. Either one
 * represents a valid destination for images.
 *
 */
public interface DataSink {

   /**
    * Called when the Acquisition is initialized 
    * @param acq
    * @param summaryMetadata 
    */
   public void initialize(Acquisition acq, JSONObject summaryMetadata);

   /**
    * Called when no more data will be collected. Ideally should block until all resources cleaned up
    */
   public void finish();

   /**
    * Is this sink and all associated resources complete (e.g. all data written to disk)
    */
   public boolean isFinished();

   /**
    * Add a new image to saving/display etc
    * @param image 
    */
   public void putImage(TaggedImage image);

   /**
    * Has putImage been called yet?
    * @return 
    */
   public boolean anythingAcquired();

}
