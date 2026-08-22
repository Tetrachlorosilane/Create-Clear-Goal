package net.Tetrachlorosilane.createcleargoal.content.productreturn;

/**
 * Identifies a promise route in the Product Return Station queue.
 * <p>
 * A promise is only fully identified by both the product type and the resolved
 * output address; the same item promised to different return addresses must
 * never share a queue entry.
 */
public record RouteKey(ItemKey item, String outputAddress) {

	public RouteKey {
		outputAddress = outputAddress == null ? "" : outputAddress;
	}
}
