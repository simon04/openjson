/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.github.openjson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Note: this class was written without inspecting the non-free org.json sourcecode.

/**
 * Implements {@link JSONObject#toString} and {@link JSONArray#toString}. Most
 * application developers should use those methods directly and disregard this
 * API. For example:<pre>
 * JSONObject object = ...
 * String json = object.toString();</pre>
 *
 * <p>Stringers only encode well-formed JSON strings. In particular:
 * <ul>
 * <li>The stringer must have exactly one top-level array or object.
 * <li>Lexical scopes must be balanced: every call to {@link #array} must
 * have a matching call to {@link #endArray} and every call to {@link
 * #object} must have a matching call to {@link #endObject}.
 * <li>Arrays may not contain keys (property names).
 * <li>Objects must alternate keys (property names) and values.
 * <li>Values are inserted with either literal {@link #value(Object) value}
 * calls, or by nesting arrays or objects.
 * </ul>
 * Calls that would result in a malformed JSON string will fail with a
 * {@link JSONException}.
 *
 * <p>This class provides no facility for pretty-printing (ie. indenting)
 * output. To encode indented output, use {@link JSONObject#toString(int)} or
 * {@link JSONArray#toString(int)}.
 *
 * <p>Some implementations of the API support at most 20 levels of nesting.
 * Attempts to create more than 20 levels of nesting may fail with a {@link
 * JSONException}.
 *
 * <p>Each stringer may be used to encode a single top level value. Instances of
 * this class are not thread safe. Although this class is nonfinal, it was not
 * designed for inheritance and should not be subclassed. In particular,
 * self-use by overrideable methods is not specified. See <i>Effective Java</i>
 * Item 17, "Design and Document or inheritance or else prohibit it" for further
 * information.
 */
public class JSONStringer {

    /**
     * The output data, containing at most one top-level array or object.
     */
    final StringBuilder out = new StringBuilder();

    /**
     * Lexical scoping elements within this stringer, necessary to insert the
     * appropriate separator characters (ie. commas and colons) and to detect
     * nesting errors.
     */
    enum Scope {

        /**
         * An array with no elements requires no separators or newlines before
         * it is closed.
         */
        EMPTY_ARRAY,

        /**
         * A array with at least one value requires a comma and newline before
         * the next element.
         */
        NONEMPTY_ARRAY,

        /**
         * An object with no keys or values requires no separators or newlines
         * before it is closed.
         */
        EMPTY_OBJECT,

        /**
         * An object whose most recent element is a key. The next element must
         * be a value.
         */
        DANGLING_KEY,

        /**
         * An object with at least one name/value pair requires a comma and
         * newline before the next element.
         */
        NONEMPTY_OBJECT,

        /**
         * A special bracketless array needed by JSONStringer.join() and
         * JSONObject.quote() only. Not used for JSON encoding.
         */
        NULL,
    }

    /**
     * Unlike the original implementation, this stack isn't limited to 20
     * levels of nesting.
     */
    private final List<Scope> stack = new ArrayList<Scope>();

    /**
     * A string containing a full set of spaces for a single level of
     * indentation, or null for no pretty printing.
     */
    private final String indent;

    public JSONStringer() {
        indent = null;
    }

    JSONStringer(int indentSpaces) {
        char[] indentChars = new char[indentSpaces];
        Arrays.fill(indentChars, ' ');
        indent = new String(indentChars);
    }

    /**
     * Begins encoding a new array. Each call to this method must be paired with
     * a call to {@link #endArray}.
     *
     * @return this stringer.
     * @throws JSONException On internal errors. Shouldn't happen.
     */
    public JSONStringer array() throws JSONException {
        return open(Scope.EMPTY_ARRAY, "[");
    }

    /**
     * Ends encoding the current array.
     *
     * @return this stringer.
     * @throws JSONException On internal errors. Shouldn't happen.
     */
    public JSONStringer endArray() throws JSONException {
        return close(Scope.EMPTY_ARRAY, Scope.NONEMPTY_ARRAY, "]");
    }

    /**
     * Begins encoding a new object. Each call to this method must be paired
     * with a call to {@link #endObject}.
     *
     * @return this stringer.
     * @throws JSONException On internal errors. Shouldn't happen.
     */
    public JSONStringer object() throws JSONException {
        return open(Scope.EMPTY_OBJECT, "{");
    }

    /**
     * Ends encoding the current object.
     *
     * @return this stringer.
     * @throws JSONException On internal errors. Shouldn't happen.
     */
    public JSONStringer endObject() throws JSONException {
        return close(Scope.EMPTY_OBJECT, Scope.NONEMPTY_OBJECT, "}");
    }

    /**
     * Enters a new scope by appending any necessary whitespace and the given
     * bracket.
     */
    JSONStringer open(Scope empty, String openBracket) throws JSONException {
        if (stack.isEmpty() && out.length() > 0) {
            throw new JSONException("Nesting problem: multiple top-level roots");
        }
        beforeValue();
        stack.add(empty);
        out.append(openBracket);
        return this;
    }

    /**
     * Closes the current scope by appending any necessary whitespace and the
     * given bracket.
     */
    JSONStringer close(Scope empty, Scope nonempty, String closeBracket) throws JSONException {
        Scope context = peek();
        if (context != nonempty && context != empty) {
            throw new JSONException("Nesting problem");
        }

        stack.remove(stack.size() - 1);
        if (context == nonempty) {
            newline();
        }
        out.append(closeBracket);
        return this;
    }

    /**
     * Returns the value on the top of the stack.
     */
    private Scope peek() throws JSONException {
        if (stack.isEmpty()) {
            throw new JSONException("Nesting problem");
        }
        return stack.get(stack.size() - 1);
    }

    /**
     * Replace the value on the top of the stack with the given value.
     */
    private void replaceTop(Scope topOfStack) {
        stack.set(stack.size() - 1, topOfStack);
    }

    /**
     * Encodes {@code value}.
     *
     * @param value a {@link JSONObject}, {@link JSONArray}, String, Boolean,
     *              Integer, Long, Double or null. May not be {@link Double#isNaN() NaNs}
     *              or {@link Double#isInfinite() infinities}.
     * @return this stringer.
     * @throws JSONException On internal errors. Shouldn't happen.
     */
    public JSONStringer value(Object value) throws JSONException {
        if (stack.isEmpty()) {
            throw new JSONException("Nesting problem");
        }

        if (value instanceof JSONArray) {
            ((JSONArray) value).writeTo(this);
            return this;

        } else if (value instanceof JSONObject) {
            ((JSONObject) value).writeTo(this);
            return this;
        }

        beforeValue();

        if (value instanceof JSONString) {
          out.append(((JSONString) value).toJSONString());
          return this;
        }

        if (value == null
              || value instanceof Boolean
              || value == JSONObject.NULL) {
            out.append(value);

        } else if (value instanceof Number) {
            out.append(JSONObject.numberToString((Number) value));

        } else {
            // Hack to make it possible that the value is not surrounded by quotes. (Used for JavaScript function calls)
            // Example: { "name": "testkey", "value": window.myfunction() }
            if (value.getClass().getSimpleName().contains("JSONFunction")) {
                // note that no escaping of quotes (or anything else) is done in this case.
                // that is fine because the only way to get to this point is to
                // explicitly put a special kind of object into the JSON data structure.
                out.append(value);
            } else {
                string(value.toString());
            }
        }

        return this;
    }

    /**
     * Encodes {@code value} to this stringer.
     *
     * @param value The value to encode.
     * @return this stringer.
     * @throws JSONException On internal errors. Shouldn't happen.
     */
    public JSONStringer value(boolean value) throws JSONException {
        if (stack.isEmpty()) {
            throw new JSONException("Nesting problem");
        }
        beforeValue();
        out.append(value);
        return this;
    }

    /**
     * Encodes {@code value} to this stringer.
     *
     * @param value a finite value. May not be {@link Double#isNaN() NaNs} or
     *              {@link Double#isInfinite() infinities}.
     * @return this stringer.
     * @throws JSONException On internal errors. Shouldn't happen.
     */
    public JSONStringer value(double value) throws JSONException {
        if (stack.isEmpty()) {
            throw new JSONException("Nesting problem");
        }
        beforeValue();
        out.append(JSONObject.numberToString(value));
        return this;
    }

    /**
     * Encodes {@code value} to this stringer.
     *
     * @param value The value to encode.
     * @return this stringer.
     * @throws JSONException If we have an internal error. Shouldn't happen.
     */
    public JSONStringer value(long value) throws JSONException {
        if (stack.isEmpty()) {
            throw new JSONException("Nesting problem");
        }
        beforeValue();
        out.append(value);
        return this;
    }

    private void string(String value) {
        out.append("\"");
        char currentChar = 0;

        for (int i = 0, length = value.length(); i < length; i++) {
            char previousChar = currentChar;
            currentChar = value.charAt(i);

            /*
             * From RFC 4627, "All Unicode characters may be placed within the
             * quotation marks except for the characters that must be escaped:
             * quotation mark, reverse solidus, and the control characters
             * (U+0000 through U+001F)."
             */
            switch (currentChar) {
                case '"':
                case '\\':
                    out.append('\\').append(currentChar);
                    break;

                case '/':
                    // it makes life easier for HTML embedding of javascript if we escape </ sequences
                    if (previousChar == '<') {
                        out.append('\\');
                    }
                    out.append(currentChar);
                    break;

                case '\t':
                    out.append("\\t");
                    break;

                case '\b':
                    out.append("\\b");
                    break;

                case '\n':
                    out.append("\\n");
                    break;

                case '\r':
                    out.append("\\r");
                    break;

                case '\f':
                    out.append("\\f");
                    break;

                default:
                    if (currentChar <= 0x1F) {
                        out.append(String.format("\\u%04x", (int) currentChar));
                    } else {
                        out.append(currentChar);
                    }
                    break;
            }

        }
        out.append("\"");
    }

    private void newline() {
        if (indent == null) {
            return;
        }

        out.append("\n");
        for (int i = 0; i < stack.size(); i++) {
            out.append(indent);
        }
    }

    /**
     * Creates String representation of the key (property name) to this stringer
     * Override this method to provide your own representation of the name.
     *
     * @param name the name of the forthcoming value.
     * @return this stringer.
     */
    protected JSONStringer createKey(String name) {
        string(name);
        return this;
    }

    /**
     * Encodes the key (property name) to this stringer.
     *
     * @param name the name of the forthcoming value. May not be null.
     * @return this stringer.
     * @throws JSONException on internal errors, shouldn't happen.
     */
    public JSONStringer key(String name) throws JSONException {
        if (name == null) {
            throw new JSONException("Names must be non-null");
        }
        beforeKey();
        return createKey(name);
    }

    /**
     * Inserts any necessary separators and whitespace before a name. Also
     * adjusts the stack to expect the key's value.
     */
    private void beforeKey() throws JSONException {
        Scope context = peek();
        if (context == Scope.NONEMPTY_OBJECT) { // first in object
            out.append(',');
        } else if (context != Scope.EMPTY_OBJECT) { // not in an object!
            throw new JSONException("Nesting problem");
        }
        newline();
        replaceTop(Scope.DANGLING_KEY);
    }

    /**
     * Inserts any necessary separators and whitespace before a literal value,
     * inline array, or inline object. Also adjusts the stack to expect either a
     * closing bracket or another element.
     */
    private void beforeValue() throws JSONException {
        if (stack.isEmpty()) {
            return;
        }

        Scope context = peek();
        if (context == Scope.EMPTY_ARRAY) { // first in array
            replaceTop(Scope.NONEMPTY_ARRAY);
            newline();
        } else if (context == Scope.NONEMPTY_ARRAY) { // another in array
            out.append(',');
            newline();
        } else if (context == Scope.DANGLING_KEY) { // value for key
            out.append(indent == null ? ":" : ": ");
            replaceTop(Scope.NONEMPTY_OBJECT);
        } else if (context != Scope.NULL) {
            throw new JSONException("Nesting problem");
        }
    }

    /**
     * Returns the encoded JSON string.
     *
     * <p>If invoked with unterminated arrays or unclosed objects, this method's
     * return value is undefined.
     *
     * <p><strong>Warning:</strong> although it contradicts the general contract
     * of {@link Object#toString}, this method returns null if the stringer
     * contains no data.
     */
    @Override
    public String toString() {
        return out.length() == 0 ? null : out.toString();
    }
}
