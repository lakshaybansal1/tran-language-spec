package Interpreter;

import AST.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class Interpreter {


    private final TranNode tran;


    public Interpreter(TranNode top) {
        this.tran = top;
    }


    public void start() {
        for (ClassNode cls : tran.Classes) {
            for (MethodDeclarationNode method : cls.methods) {

                if (method.name.equals("start") && method.parameters.isEmpty()) {
                    interpretMethodCall(Optional.empty(), method, new LinkedList<>());
                    return;
                }
            }
        }
        throw new RuntimeException("No 'start' method found");
    }



    private List<InterpreterDataType> interpretMethodCall(Optional<ObjectIDT> object, MethodDeclarationNode m, List<InterpreterDataType> values) {
        if (m.parameters.size() != values.size()) {
            throw new RuntimeException("Method " + m.name + " expects " + m.parameters.size() +
                    " parameters but got " + values.size());
        }
        HashMap<String, InterpreterDataType> locals = new HashMap<>();

        // Bind parameters.
        for (int i = 0; i < m.parameters.size(); i++) {
            VariableDeclarationNode parDecl = m.parameters.get(i);
            locals.put(parDecl.name, values.get(i));
        }


        if (m.returns != null) {
            for (VariableDeclarationNode retDecl : m.returns) {
                InterpreterDataType retVal = instantiate(retDecl.type);
                locals.put(retDecl.name, retVal);
            }
        }


        for (VariableDeclarationNode localDecl : m.locals) {
            InterpreterDataType varVal = instantiate(localDecl.type);
            if (localDecl.initializer.isPresent()) {
                InterpreterDataType initVal = evaluate(locals, object, localDecl.initializer.get());
                varVal.Assign(initVal);
            }
            locals.put(localDecl.name, varVal);
        }


        interpretStatementBlock(object, m.statements, locals);


        LinkedList<InterpreterDataType> ret = new LinkedList<>();
        if (m.returns != null) {
            for (VariableDeclarationNode retDecl : m.returns) {
                ret.add(locals.get(retDecl.name));
            }
        }
        return ret;
    }



    private void interpretStatementBlock(Optional<ObjectIDT> object, List<StatementNode> statements, HashMap<String, InterpreterDataType> locals) {
        for (StatementNode stmt : statements) {
            if (stmt instanceof AssignmentNode) {
                AssignmentNode assign = (AssignmentNode) stmt;
                InterpreterDataType value = evaluate(locals, object, assign.expression);
                InterpreterDataType variable = findVariable(assign.target.name, locals, object);
                variable.Assign(value);
            } else if (stmt instanceof MethodCallStatementNode) {
                MethodCallStatementNode callStmt = (MethodCallStatementNode) stmt;
                evaluateMethodCallStatement(callStmt, locals, object);
            } else if (stmt instanceof IfNode) {
                IfNode ifNode = (IfNode) stmt;
                InterpreterDataType condVal = evaluate(locals, object, ifNode.condition);
                if (!(condVal instanceof BooleanIDT)) {
                    throw new RuntimeException("If condition did not evaluate to a boolean");
                }
                if (((BooleanIDT) condVal).Value) {
                    interpretStatementBlock(object, ifNode.statements, locals);
                } else if (ifNode.elseStatement.isPresent()) {
                    interpretStatementBlock(object, ifNode.elseStatement.get().statements, locals);
                }
            } else if (stmt instanceof LoopNode) {
                LoopNode loopNode = (LoopNode) stmt;
                while (true) {
                    InterpreterDataType condVal = evaluate(locals, object, loopNode.expression);
                    if (!(condVal instanceof BooleanIDT)) {
                        throw new RuntimeException("Loop condition did not evaluate to a boolean");
                    }
                    if (!((BooleanIDT) condVal).Value)
                        break;
                    if (loopNode.assignment.isPresent()) {
                        VariableReferenceNode assignVar = loopNode.assignment.get();
                        InterpreterDataType var = findVariable(assignVar.name, locals, object);
                        var.Assign(condVal);
                    }
                    interpretStatementBlock(object, loopNode.statements, locals);
                }
            } else if (stmt instanceof VariableDeclarationNode) {
                VariableDeclarationNode decl = (VariableDeclarationNode) stmt;
                InterpreterDataType initVal = instantiate(decl.type);
                if (decl.initializer.isPresent()) {
                    initVal.Assign(evaluate(locals, object, decl.initializer.get()));
                }
                locals.put(decl.name, initVal);
            } else {
                throw new RuntimeException("Unknown statement type: " + stmt.getClass());
            }
        }
    }


    private void evaluateMethodCallStatement(MethodCallStatementNode callStmt,
                                             HashMap<String, InterpreterDataType> locals,
                                             Optional<ObjectIDT> currentObject) {
        List<InterpreterDataType> paramVals = new LinkedList<>();
        for (ExpressionNode paramExpr : callStmt.parameters) {
            paramVals.add(evaluate(locals, currentObject, paramExpr));
        }

        if (callStmt.objectName.isPresent() && callStmt.objectName.get().equals("console")) {
            ConsoleWrite cw = new ConsoleWrite();
            cw.Execute(paramVals);
            return;
        }

        if (callStmt.objectName.isPresent()) {
            String objName = callStmt.objectName.get().trim();
            String methName = callStmt.methodName.trim();
            if ((objName.equalsIgnoreCase("boolean") || objName.equalsIgnoreCase("boolan"))
                    && (methName.equalsIgnoreCase("true") || methName.equalsIgnoreCase("false"))) {
                return;
            }
        }

        ObjectIDT targetObj = null;
        if (callStmt.objectName.isPresent()) {
            InterpreterDataType var = findVariable(callStmt.objectName.get(), locals, currentObject);
            if (var instanceof ObjectIDT) {
                targetObj = (ObjectIDT) var;
            } else if (var instanceof ReferenceIDTWrapper) {
                ReferenceIDTWrapper ref = (ReferenceIDTWrapper) var;
                if (ref.obj != null)
                    targetObj = ref.obj;
                else
                    throw new RuntimeException("Variable " + callStmt.objectName.get() + " is not an object.");
            } else {
                throw new RuntimeException("Variable " + callStmt.objectName.get() + " is not an object.");
            }
        } else {
            if (currentObject.isPresent())
                targetObj = currentObject.get();
            else
                throw new RuntimeException("Method call " + callStmt.methodName + " has no target object");
        }
        MethodDeclarationNode targetMethod = getMethodFromObject(targetObj, callStmt.methodName, paramVals);
        interpretMethodCall(Optional.of(targetObj), targetMethod, paramVals);
    }



    private InterpreterDataType evaluate(HashMap<String, InterpreterDataType> locals,
                                         Optional<ObjectIDT> object,
                                         ExpressionNode expression) {
        if (expression instanceof NumericLiteralNode) {
            NumericLiteralNode num = (NumericLiteralNode) expression;
            return new NumberIDT(num.value);
        } else if (expression instanceof StringLiteralNode) {
            StringLiteralNode str = (StringLiteralNode) expression;
            return new StringIDT(str.value);
        } else if (expression instanceof CharLiteralNode) {
            CharLiteralNode chr = (CharLiteralNode) expression;
            return new CharIDT(chr.value);
        } else if (expression instanceof BooleanLiteralNode) {
            BooleanLiteralNode bool = (BooleanLiteralNode) expression;
            return new BooleanIDT(bool.value);
        } else if (expression instanceof VariableReferenceNode) {
            VariableReferenceNode varRef = (VariableReferenceNode) expression;
            return findVariable(varRef.name, locals, object);
        } else if (expression instanceof MathOpNode) {
            MathOpNode math = (MathOpNode) expression;
            InterpreterDataType left = evaluate(locals, object, math.left);
            InterpreterDataType right = evaluate(locals, object, math.right);
            if (math.op == MathOpNode.MathOperations.add) {
                if (left instanceof NumberIDT && right instanceof NumberIDT) {
                    return new NumberIDT(((NumberIDT) left).Value + ((NumberIDT) right).Value);
                } else {
                    return new StringIDT(left.toString() + right.toString());
                }
            } else if (math.op == MathOpNode.MathOperations.subtract) {
                return new NumberIDT(((NumberIDT) left).Value - ((NumberIDT) right).Value);
            } else if (math.op == MathOpNode.MathOperations.multiply) {
                return new NumberIDT(((NumberIDT) left).Value * ((NumberIDT) right).Value);
            } else if (math.op == MathOpNode.MathOperations.divide) {
                float rVal = ((NumberIDT) right).Value;
                if (rVal == 0)
                    throw new RuntimeException("Division by zero");
                return new NumberIDT(((NumberIDT) left).Value / rVal);
            } else if (math.op == MathOpNode.MathOperations.modulo) {
                return new NumberIDT(((NumberIDT) left).Value % ((NumberIDT) right).Value);
            } else {
                throw new RuntimeException("Unknown math operation");
            }
        } else if (expression instanceof CompareNode) {
            CompareNode cmp = (CompareNode) expression;
            InterpreterDataType left = evaluate(locals, object, cmp.left);
            InterpreterDataType right = evaluate(locals, object, cmp.right);
            if (left instanceof NumberIDT && right instanceof NumberIDT) {
                float l = ((NumberIDT) left).Value;
                float r = ((NumberIDT) right).Value;
                boolean result;
                switch (cmp.op) {
                    case eq: result = (l == r); break;
                    case ne: result = (l != r); break;
                    case lt: result = (l < r); break;
                    case le: result = (l <= r); break;
                    case gt: result = (l > r); break;
                    case ge: result = (l >= r); break;
                    default: throw new RuntimeException("Unsupported comparison operator");
                }
                return new BooleanIDT(result);
            } else if (left instanceof BooleanIDT && right instanceof BooleanIDT) {
                boolean l = ((BooleanIDT) left).Value;
                boolean r = ((BooleanIDT) right).Value;
                boolean result;
                switch (cmp.op) {
                    case eq: result = (l == r); break;
                    case ne: result = (l != r); break;
                    default: throw new RuntimeException("Only equality comparisons allowed on booleans");
                }
                return new BooleanIDT(result);
            } else {
                boolean result = left.toString().equals(right.toString());
                if (cmp.op == CompareNode.CompareOperations.ne)
                    result = !result;
                return new BooleanIDT(result);
            }
        } else if (expression instanceof MethodCallExpressionNode) {
            MethodCallExpressionNode callExpr = (MethodCallExpressionNode) expression;
            List<InterpreterDataType> paramVals = new LinkedList<>();
            for (ExpressionNode param : callExpr.parameters) {
                paramVals.add(evaluate(locals, object, param));
            }

            if (callExpr.objectName.isPresent()) {
                String objName = callExpr.objectName.get().trim();
                String methName = callExpr.methodName.trim();
                if ((objName.equalsIgnoreCase("boolean") || objName.equalsIgnoreCase("boolan"))
                        && (methName.equalsIgnoreCase("true") || methName.equalsIgnoreCase("false"))) {
                    return new BooleanIDT(methName.equalsIgnoreCase("true"));
                }
            }

            ObjectIDT targetObj = null;
            if (callExpr.objectName.isPresent()) {
                InterpreterDataType var = findVariable(callExpr.objectName.get(), locals, object);
                if (var instanceof ObjectIDT) {
                    targetObj = (ObjectIDT) var;
                } else if (var instanceof ReferenceIDTWrapper) {
                    ReferenceIDTWrapper ref = (ReferenceIDTWrapper) var;
                    if (ref.obj != null)
                        targetObj = ref.obj;
                    else
                        throw new RuntimeException("Variable " + callExpr.objectName.get() + " is not an object.");
                } else {
                    throw new RuntimeException("Variable " + callExpr.objectName.get() + " is not an object.");
                }
            } else {
                if (object.isPresent())
                    targetObj = object.get();
                else
                    throw new RuntimeException("Method call " + callExpr.methodName + " has no target object");
            }
            MethodDeclarationNode targetMethod = getMethodFromObject(targetObj, callExpr.methodName, paramVals);
            List<InterpreterDataType> retVals = interpretMethodCall(Optional.of(targetObj), targetMethod, paramVals);
            return retVals.isEmpty() ? new StringIDT("") : retVals.get(0);
        } else if (expression instanceof NewNode) {
            NewNode newExpr = (NewNode) expression;
            Optional<ClassNode> clsOpt = getClassByName(newExpr.className);
            if (!clsOpt.isPresent())
                throw new RuntimeException("Class " + newExpr.className + " not found");
            ClassNode cls = clsOpt.get();
            ObjectIDT newObj = new ObjectIDT(cls);
            // Initialize fields.
            for (MemberNode mem : cls.members) {
                InterpreterDataType fieldVal = instantiate(mem.declaration.type);
                if (mem.declaration.initializer.isPresent()) {
                    fieldVal.Assign(evaluate(new HashMap<>(), Optional.of(newObj), mem.declaration.initializer.get()));
                }
                newObj.members.put(mem.declaration.name, fieldVal);
            }
            List<InterpreterDataType> paramVals = new LinkedList<>();
            for (ExpressionNode paramExpr : newExpr.parameters) {
                paramVals.add(evaluate(locals, object, paramExpr));
            }
            ConstructorNode constructor = null;
            for (ConstructorNode cons : cls.constructors) {
                if (cons.parameters.size() == paramVals.size()) {
                    constructor = cons;
                    break;
                }
            }
            if (constructor != null) {
                HashMap<String, InterpreterDataType> consLocals = new HashMap<>();
                for (int i = 0; i < constructor.parameters.size(); i++) {
                    VariableDeclarationNode pd = constructor.parameters.get(i);
                    consLocals.put(pd.name, paramVals.get(i));
                }
                interpretStatementBlock(Optional.of(newObj), constructor.statements, consLocals);
            }
            return new ReferenceIDTWrapper(newObj);
        } else {
            throw new RuntimeException("Unknown expression type: " + expression.getClass());
        }
    }


    private InterpreterDataType findVariable(String name, HashMap<String, InterpreterDataType> locals, Optional<ObjectIDT> object) {
        if (locals.containsKey(name)) {
            return locals.get(name);
        }
        if (object.isPresent() && object.get().members.containsKey(name)) {
            return object.get().members.get(name);
        }
        throw new RuntimeException("Unable to find variable " + name);
    }

    private InterpreterDataType instantiate(String type) {
        if (type.equals("number"))
            return new NumberIDT(0);
        else if (type.equals("string"))
            return new StringIDT("");
        else if (type.equals("boolean"))
            return new BooleanIDT(false);
        else if (type.equals("character"))
            return new CharIDT(' ');
        else {
            return new ReferenceIDTWrapper(null);
        }
    }

    private Optional<ClassNode> getClassByName(String name) {
        for (ClassNode cls : tran.Classes) {
            if (cls.name.equals(name))
                return Optional.of(cls);
        }
        return Optional.empty();
    }

    private MethodDeclarationNode getMethodFromObject(ObjectIDT object, String methodName, List<InterpreterDataType> parameters) {
        for (MethodDeclarationNode m : object.astNode.methods) {
            if (m.name.equals(methodName) && m.parameters.size() == parameters.size())
                return m;
        }
        throw new RuntimeException("Unable to resolve method call " + methodName);
    }

    static class ReferenceIDTWrapper implements InterpreterDataType {
        private ObjectIDT obj;
        public ReferenceIDTWrapper(ObjectIDT obj) {
            this.obj = obj;
        }
        @Override
        public void Assign(InterpreterDataType in) {
            if (in instanceof ReferenceIDTWrapper) {
                this.obj = ((ReferenceIDTWrapper) in).obj;
            } else if (in instanceof ObjectIDT) {
                this.obj = (ObjectIDT) in;
            } else {
                throw new RuntimeException("Cannot assign " + in.getClass() + " to an object reference");
            }
        }
        @Override
        public String toString() {
            return (obj == null) ? "<<<NULL>>>" : obj.toString();
        }
    }
}
