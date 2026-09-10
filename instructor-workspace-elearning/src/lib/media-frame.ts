/**
 * The shape every moving picture on this platform is shown in.
 *
 * Course video is 16:9 and has been since it stopped being 4:3. Screen
 * recordings, slides, talking heads and everything an instructor is likely to
 * upload are authored at that ratio, and every platform a student has used
 * before this one shows them at that ratio. Following the convention is not
 * conservatism here: a player whose box does not match the video letterboxes
 * it, and a grid of thumbnails at assorted ratios does not line up.
 *
 * Fixing the frame rather than letting the media size the box also settles the
 * layout before anything has loaded. A player that has not yet worked out its
 * intrinsic size is zero pixels tall, so an unconstrained one collapses and
 * then shoves the rest of the page down when metadata arrives.
 */
export const MEDIA_FRAME = "aspect-video w-full overflow-hidden rounded-xl border bg-black";

/**
 * A still image standing in for a video: a course thumbnail, a poster frame.
 *
 * `object-cover` rather than `object-contain` because a thumbnail's job is to
 * fill its tile. An instructor who uploads something that is not 16:9 gets it
 * cropped to fit, which is the failure everyone expects, rather than pillar-
 * boxed against black, which looks like a bug.
 */
export const MEDIA_STILL = "h-full w-full object-cover";
