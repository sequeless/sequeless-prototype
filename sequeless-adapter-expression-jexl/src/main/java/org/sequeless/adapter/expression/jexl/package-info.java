/**
 * The default {@code ExpressionPort} adapter, backed by Apache Commons JEXL 3: {@link
 * org.sequeless.adapter.expression.jexl.JexlExpressionPort} evaluates {@code sq:guard} and value
 * expressions and renders {@code sq:Webhook}/{@code sq:Log} JXLT {@code ${expr}} templates, both
 * against a sandboxed {@code JexlEngine}/{@code JxltEngine} pair built with a purpose-built {@code
 * JexlPermissions} set — no reflection, no static method/class access, no file or network I/O — so
 * an expression authored by a tenant in the ontology can never escape the {@code self}/{@code
 * state}/{@code principal} bindings it is evaluated against. {@link
 * org.sequeless.adapter.expression.jexl.JexlExpressionAutoConfiguration} wires the port bean when
 * {@code sequeless.expression.adapter=jexl}.
 */
package org.sequeless.adapter.expression.jexl;
