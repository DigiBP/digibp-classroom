package ch.fhnw.digibp.classroom.config;

import org.cibseven.bpm.engine.impl.scripting.engine.JuelScriptEngineFactory;
import org.cibseven.bpm.impl.juel.ExpressionFactoryImpl;
import org.cibseven.bpm.impl.juel.SimpleContext;
import org.cibseven.bpm.impl.juel.jakarta.el.ArrayELResolver;
import org.cibseven.bpm.impl.juel.jakarta.el.CompositeELResolver;
import org.cibseven.bpm.impl.juel.jakarta.el.ELContext;
import org.cibseven.bpm.impl.juel.jakarta.el.ELException;
import org.cibseven.bpm.impl.juel.jakarta.el.ListELResolver;
import org.cibseven.bpm.impl.juel.jakarta.el.MapELResolver;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Data-only JUEL scripts: no Java objects, bean getters, methods or imported functions. */
final class RestrictedJuelScriptEngine extends AbstractScriptEngine {
    private static final Set<Class<?>> SCALAR_TYPES = Set.of(String.class, Boolean.class, Character.class,
            Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
            BigInteger.class, BigDecimal.class);
    private final ExpressionFactoryImpl expressions;

    RestrictedJuelScriptEngine() {
        Properties properties = new Properties();
        properties.setProperty(ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS, "false");
        expressions = new ExpressionFactoryImpl(properties);
    }

    @Override
    public Object eval(String script, ScriptContext scriptContext) throws ScriptException {
        // Never reuse an ELContext or FunctionMapper supplied through script bindings.
        CompositeELResolver resolver = new CompositeELResolver() {
            @Override
            public Object getValue(ELContext context, Object base, Object property) {
                if (base == null && property instanceof String name
                        && scriptContext.getAttributesScope(name) != -1) {
                    context.setPropertyResolved(true);
                    return copyData(scriptContext.getAttribute(name), 0);
                }
                return super.getValue(context, base, property);
            }
        };
        resolver.add(new MapELResolver(true));
        resolver.add(new ListELResolver(true));
        resolver.add(new ArrayELResolver(true));
        SimpleContext context = new SimpleContext(resolver);
        try {
            return expressions.createValueExpression(context, script, Object.class).getValue(context);
        } catch (ELException e) {
            throw new ScriptException(e);
        }
    }

    private static Object copyData(Object value, int depth) {
        if (value == null || SCALAR_TYPES.contains(value.getClass())) {
            return value;
        }
        if (depth >= 64) {
            throw new ELException("JUEL data is cyclic or nested too deeply.");
        }
        // Copy containers too: nested Java objects must not reach EL coercion/toString.
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(copyData(key, depth + 1), copyData(item, depth + 1)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(copyData(item, depth + 1));
            }
            return copy;
        }
        if (value.getClass().isArray()) {
            Object[] copy = new Object[Array.getLength(value)];
            for (int i = 0; i < copy.length; i++) {
                copy[i] = copyData(Array.get(value, i), depth + 1);
            }
            return copy;
        }
        throw new ELException("JUEL scripts only allow scalar values, maps, lists and arrays.");
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        StringBuilder script = new StringBuilder();
        char[] buffer = new char[4096];
        try {
            for (int count; (count = reader.read(buffer)) != -1;) {
                script.append(buffer, 0, count);
            }
        } catch (IOException e) {
            throw new ScriptException(e);
        }
        return eval(script.toString(), context);
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return new JuelScriptEngineFactory();
    }
}
