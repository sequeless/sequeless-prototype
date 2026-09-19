package org.sequeless.adapter.expression.jexl;

import org.sequeless.spi.expression.ExpressionPort;
import org.sequeless.testkit.expression.ExpressionContract;

class JexlExpressionPortContractTest extends ExpressionContract {

    @Override
    protected ExpressionPort port() {
        return new JexlExpressionPort();
    }
}
