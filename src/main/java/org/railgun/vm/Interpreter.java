package org.railgun.vm;

import javafx.scene.paint.Color;
import org.railgun.action.ActionController;
import org.railgun.canvas.View;
import org.railgun.marshal.BinaryFileParser;
import org.railgun.marshal.CodeObject;
import org.railgun.shape.Circle;
import org.railgun.shape.Shape;

import java.util.*;

public class Interpreter {

    // Stack based Virtual Machine
    private static class Frame {
        public List<Object> consts;
        public List<Object> names;
        public Map<String, Object> varnamesTable;
        public List<Object> varnames;
        public Stack<Object> stack;
        public byte[] optArr;
        public int pc;

        Frame(List<Object> consts, List<Object> names, List<Object> varnames, byte[] optArr, Stack<Object> stack, int pc) {
            this.consts = consts;
            this.names = names;
            this.varnames = varnames;
            varnamesTable = new HashMap<>();
            this.stack = stack;
            this.optArr = optArr;
            this.pc = pc;
        }
    }

    private static Interpreter instance = new Interpreter();

    private static boolean x86 = false;

    public static Interpreter getInstance() {
        return instance;
    }

    // Init stack trace
    Stack<Frame> stackTrace = new Stack<>();
    // Global variable table
    private Map<String, Object> namesTable = new HashMap<>();

    // Run with source bytes
    public void run (byte[] sourceBytes) {
        // Read code object from bytecode
        CodeObject co = BinaryFileParser.parse(sourceBytes);

        run(co);
    }

    // Run with code object
    public void run (CodeObject co) {
        // Construct base frame
        Frame baseFrame = new Frame(co.consts, co.names, co.varnames, co.bytecodes, new Stack<>(), 0);

        // Interpret current frame
        interpret(baseFrame, new Stack<>());
    }

    // Interpret Instructions
    void interpret (Frame curFrame, Stack<Frame> stackTrace) {
        // Program Counter
        int pc = curFrame.pc;
        // Bytecodes Array
        byte[] optArr = curFrame.optArr;
        // Bytecode Array Length
        int optLength = optArr.length;

        // Bytecode constant pools
        List<Object> consts = curFrame.consts;
        // Bytecode local variable
        List<Object> varnames = curFrame.varnames;
        Map<String, Object> varnamesTable = curFrame.varnamesTable;
        // Bytecode global variable
        List<Object> names = curFrame.names;
        Stack<Object> stack = curFrame.stack;

        while (pc < optLength) {
            // TODO: Make sure current pc is at optcode, not optarg
            byte optcode = optArr[pc++];
            boolean haveArgument = (optcode & 0xFF) >= Bytecode.HAVE_ARGUMENT;
            int optarg = -1;
            if (haveArgument) {
                optarg = x86 ? (optArr[pc++] & 0xFF) : ((optArr[pc++] & 0xFF) + ((optArr[pc++] & 0xFF) << 8));
            }
            Integer lhs, rhs;
            Object v, w, attr;
            switch (optcode) {
                case Bytecode.POP_TOP:
                    stack.pop();
                    break;
                // 2
                case Bytecode.ROT_TWO:
                    v = stack.pop();
                    w = stack.pop();
                    stack.push(v);
                    stack.push(w);
                    break;
                // 4
                case Bytecode.DUP_TOP:
                    stack.push(stack.peek());
                    break;
                case Bytecode.UNARY_NEGATIVE:
                    v = stack.pop();
                    stack.push(-((Integer)v).intValue());
                    break;
                // 57
                case Bytecode.INPLACE_MULTIPLY:
                    // 20
                case Bytecode.BINARY_MULTIPLY:
                    rhs = (Integer) stack.pop();
                    lhs = (Integer) stack.pop();
                    stack.push(lhs * rhs);
                    break;
                // 58
                case Bytecode.INPLACE_DIVIDE:
                    // 21
                case Bytecode.BINARY_DIVIDE:
                    rhs = (Integer) stack.pop();
                    lhs = (Integer) stack.pop();
                    stack.push(lhs / rhs);
                    break;
                // 55
                case Bytecode.INPLACE_ADD:
                    // 23
                case Bytecode.BINARY_ADD:
                    rhs = (Integer) stack.pop();
                    lhs = (Integer) stack.pop();
                    stack.push(lhs + rhs);
                    break;
                // 56
                case Bytecode.INPLACE_SUBSTRACT:
                    // 24
                case Bytecode.BINARY_SUBSTRACT:
                    rhs = (Integer) stack.pop();
                    lhs = (Integer) stack.pop();
                    stack.push(lhs - rhs);
                    break;
                // 71
                case Bytecode.PRINT_ITEM:
                    Object printObject = stack.pop();

                    if (printObject instanceof ArrayList) {
                        for (Object obj : (ArrayList)printObject) {
                            if (obj instanceof Shape) {
                                View.getView().drawShape((Shape)obj);
                            }
                        }
                    }
                    else if (printObject instanceof Shape) {
                        View.getView().drawShape((Shape) printObject);
                    }

                    System.out.print(printObject);
                    break;
                // 72
                case Bytecode.PRINT_NEWLINE:
                    System.out.println();
                    break;
                // 83
                case Bytecode.RETURN_VALUE:
                    if (! stackTrace.empty()) {
                        curFrame = stackTrace.pop();
                        curFrame.stack.push(stack.pop());
                        consts = curFrame.consts;
                        optArr = curFrame.optArr;
                        names = curFrame.names;
                        varnames = curFrame.varnames;
                        varnamesTable = curFrame.varnamesTable;
                        stack = curFrame.stack;
                        pc = curFrame.pc;
                        optLength = optArr.length;
                    }
                    break;

                // 95
                case Bytecode.STORE_ATTR:
                    v = stack.pop();
                    w = stack.pop();

                    attr = (String)names.get(optarg);

                    if (v instanceof Shape) {
                        if (attr.equals("x")) {
                            ((Shape)v).setX((Integer)w);
                        }
                        else if (attr.equals("y")) {
                            ((Shape)v).setY((Integer)w);
                        }
                    }

                    break;

                // TODO: Have Argument
                // 97
                case Bytecode.STORE_GLOBAL:
                // 90
                case Bytecode.STORE_NAME:
                    String checkKeyMap = (String)names.get(optarg);
                    Object checkKeyMapObject = stack.pop();

                    if (checkKeyMap.equals("KeyMap")) {
                        ActionController.getActionController().setKeyMap((HashMap) checkKeyMapObject);
                    }
                    else if (checkKeyMap.equals("update") && checkKeyMapObject instanceof CodeObject) {
                        ActionController.getActionController().setUpdateFunction((CodeObject)checkKeyMapObject);
                    }

                    namesTable.put(checkKeyMap, checkKeyMapObject);

                    break;
                // 116
                case Bytecode.LOAD_GLOBAL:
                // 101
                case Bytecode.LOAD_NAME:
                    String viariableName = (String)names.get(optarg);

                    if (viariableName.equals("circle") || viariableName.equals("rgb")) {
                        stack.push(viariableName);
                    }
                    else {
                        stack.push(namesTable.get(viariableName));
                    }
                    break;
                // 100
                case Bytecode.LOAD_CONST:
                    stack.push(consts.get(optarg));
                    break;
                case Bytecode.LOAD_ATTR:
                    v = stack.pop();
                    w = names.get(optarg);

                    if (v instanceof Shape) {
                        if (((String)w).equals("x")) {
                            stack.push(((Shape)v).getX());
                        }
                        else if (((String)w).equals("y")) {
                            stack.push(((Shape)v).getY());
                        }
                    }
                    break;

                // 107
                case Bytecode.COMPARE_OP:
                    // TODO: Only for Integer
                    rhs = (Integer) stack.pop();
                    lhs = (Integer) stack.pop();
                    switch (optarg) {
                        case Bytecode.COMPARE.LESS:
                            stack.push(lhs < rhs);
                            break;
                        case Bytecode.COMPARE.LESS_EQUAL:
                            stack.push(lhs <= rhs);
                            break;
                        case Bytecode.COMPARE.EQUAL:
                            stack.push(lhs == rhs);
                            break;
                        case Bytecode.COMPARE.NOT_EQUAL:
                            stack.push(lhs != rhs);
                            break;
                        case Bytecode.COMPARE.GREATER:
                            stack.push(lhs > rhs);
                            break;
                        case Bytecode.COMPARE.GREATER_EQUAL:
                            stack.push(lhs >= rhs);
                            break;
                    }
                    break;
                // 113
                case Bytecode.JUMP_ABSOLUTE:
                    pc = optarg;
                    break;
                // 114
                case Bytecode.POP_JUMP_IF_FALSE:
                    if (! (Boolean) stack.pop())
                        pc = optarg;
                    break;
                // 115
                case Bytecode.POP_JUMP_IF_TRUE:
                    if ((Boolean) stack.pop())
                        pc = optarg;
                    break;
                // 120
                case Bytecode.SETUP_LOOP:
                    break;
                // 124
                case Bytecode.LOAD_FAST:
                    String fastVarName = (String)varnames.get(optarg);

                    if (fastVarName.equals("circle") || fastVarName.equals("rgb")) {
                        stack.push(fastVarName);
                    }
                    else {
                        stack.push(varnamesTable.get(fastVarName));
                    }
                    break;
                // 125
                case Bytecode.STORE_FAST:
                    varnamesTable.put((String)varnames.get(optarg), stack.pop());
                    break;
                // 131
                case Bytecode.CALL_FUNCTION:
                    // Process Callee Arguments
                    Object[] nextArgs = new Object[optarg];
                    for (int i = 0; i < optarg; ++i) {
                       nextArgs[i] = stack.pop();
                    }
                    Object o = stack.pop();

                    if (o instanceof String) {
                        String funcName = (String) o;
                        if (funcName.equals("circle")) {
                            stack.push(Circle.makeCircle((Integer) nextArgs[3],
                                    (Integer)nextArgs[2],
                                    ((Integer) nextArgs[1]).doubleValue(),
                                    (Color)nextArgs[0]));
                        }
                        else if (funcName.equals("rgb")) {
                            stack.push(Color.rgb((Integer)nextArgs[2], (Integer)nextArgs[1], (Integer)nextArgs[0]));
                        }
                    } else {
                        curFrame.pc = pc;
                        stackTrace.push(curFrame);
                        CodeObject co = (CodeObject) o;
                        curFrame = new Frame(co.consts, co.names, co.varnames, co.bytecodes, new Stack<>(), 0);

                        consts = curFrame.consts;
                        names = curFrame.names;
                        varnames = curFrame.varnames;
                        varnamesTable = curFrame.varnamesTable;
                        optArr = curFrame.optArr;
                        stack = curFrame.stack;
                        pc = curFrame.pc;
                        optLength = optArr.length;
                        // Process Input Arguments
                        for (int i = nextArgs.length-1; i >= 0; --i) {
                            varnamesTable.put((String) varnames.get(nextArgs.length - 1 - i), nextArgs[i]);
                        }
                    }
                    break;
                // 132
                case Bytecode.MAKE_FUNCTION:
                    break;
                case Bytecode.BUILD_LIST:
                    ArrayList<Object> arr=new ArrayList<Object>();
                    for (int i = 0; i < optarg; ++i) {
                        arr.add(stack.pop());
                    }
                    stack.push(arr);
                    break;
                //105
                case Bytecode.BUILD_MAP:
                    HashMap<Object, Object> mp = new HashMap<Object, Object>();
                    stack.push(mp);
                    break;
                //54
                case Bytecode.STORE_MAP:
                    Object objLeft = stack.pop();
                    Object objRight = stack.pop();
                    //mp.put(objLeft, objRight);
                    HashMap<Object, Object> map = (HashMap<Object, Object>) (stack.peek());
                    map.put(objLeft, objRight);
                    break;
            }
        }
    }

}

