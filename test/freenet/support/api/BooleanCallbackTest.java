package freenet.support.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import org.hamcrest.Matchers;
import org.junit.Test;

import junit.framework.TestCase;

import freenet.config.InvalidConfigValueException;
import freenet.config.NodeNeedRestartException;

public class BooleanCallbackTest {

  boolean theValue = false;

  @Test
  public void canCreateBooleanCallbackFromLambdas()
      throws NodeNeedRestartException, InvalidConfigValueException {
    BooleanCallback callback = BooleanCallback.from(() -> true, (value) -> theValue = value);
    callback.set(true);

    assertThat(theValue, Matchers.is(true));
  }
  @Test
  public void canThrowInvalidConfigValueException()
      throws NodeNeedRestartException, InvalidConfigValueException {
    BooleanCallback callback = BooleanCallback.from(() -> true, (value) -> {
      throw new InvalidConfigValueException("invalid");
    });
    assertThrows(InvalidConfigValueException.class, () -> callback.set(true));

  }
  @Test
  public void canThrowNodeNeedRestartException()
      throws NodeNeedRestartException, InvalidConfigValueException {
    BooleanCallback callback = BooleanCallback.from(() -> true, (value) -> {
      throw new NodeNeedRestartException("needs restart");
    });
    assertThrows(NodeNeedRestartException.class, () -> callback.set(true));

  }

}
