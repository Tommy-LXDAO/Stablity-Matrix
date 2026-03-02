#!/bin/bash

# =============================================================================
# run-automation.sh - Automated Task Runner for Claude Code
# =============================================================================
# This script runs Claude Code multiple times in a loop to automatically
# complete tasks defined in task.json
#
# Usage:
#   ./run-automation.sh <number_of_runs>
#   ./run-automation.sh 5        # Run 5 iterations
#   ./run-automation.sh 1        # Run single iteration
#
# Requirements:
#   - Claude CLI installed and authenticated
#   - task.json file in current directory
# =============================================================================

set -e

# Colors for logging
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

# Configuration
LOG_DIR="./automation-logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/automation-$(date +%Y%m%d_%H%M%S).log"
MAX_CONSECUTIVE_FAILURES=3

# Counters
CONSECUTIVE_FAILURES=0

# Function to log messages
log() {
    local level=$1
    local message=$2
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "${timestamp} [${level}] ${message}" >> "$LOG_FILE"

    case $level in
        INFO)
            echo -e "${BLUE}[INFO]${NC} ${message}"
            ;;
        SUCCESS)
            echo -e "${GREEN}[SUCCESS]${NC} ${message}"
            ;;
        WARNING)
            echo -e "${YELLOW}[WARNING]${NC} ${message}"
            ;;
        ERROR)
            echo -e "${RED}[ERROR]${NC} ${message}"
            ;;
        PROGRESS)
            echo -e "${CYAN}[PROGRESS]${NC} ${message}"
            ;;
        DEBUG)
            echo -e "${MAGENTA}[DEBUG]${NC} ${message}"
            ;;
    esac
}

# Function to count remaining tasks
count_remaining_tasks() {
    if [ -f "task.json" ]; then
        local count=$(grep -c '"passes": false' task.json 2>/dev/null || echo "0")
        echo "$count"
    else
        echo "0"
    fi
}

# Function to count blocked tasks
count_blocked_tasks() {
    if [ -f "task.json" ]; then
        local count=$(grep -c '"blocked": true' task.json 2>/dev/null || echo "0")
        echo "$count"
    else
        echo "0"
    fi
}

# Function to get next task ID
get_next_task_id() {
    if [ -f "task.json" ]; then
        # Find first task with passes: false (tasks should be in order)
        local id=$(grep -B3 '"passes": false' task.json | grep '"id"' | head -1 | grep -o '[0-9]*')
        echo "$id"
    else
        echo ""
    fi
}

# Function to check prerequisites
check_prerequisites() {
    log "INFO" "Checking prerequisites..."

    # Check if claude CLI exists
    if ! command -v claude &> /dev/null; then
        log "ERROR" "Claude CLI not found. Please install it first."
        exit 1
    fi

    # Check if task.json exists
    if [ ! -f "task.json" ]; then
        log "ERROR" "task.json not found! Please run this script from the project root."
        exit 1
    fi

    # Check if CLAUDE.md exists
    if [ ! -f "CLAUDE.md" ]; then
        log "WARNING" "CLAUDE.md not found. Agent may not have proper instructions."
    fi

    log "SUCCESS" "Prerequisites check passed"
}

# Function to create the prompt
create_prompt() {
    local next_task_id=$1
    local remaining=$2

    cat << PROMPT_EOF
You are an AI agent working on the Stability Matrix project.

## Your Mission
Complete ONE task from task.json following the Agent Workflow in CLAUDE.md.

## Current Status
- Remaining tasks: ${remaining}
- Next task ID: ${next_task_id}

## Workflow (follow exactly)

### Phase 1: Task Selection
1. Read task.json
2. Select task with ID ${next_task_id} (lowest ID with passes: false)
3. Read the task description and all steps

### Phase 2: Implementation
1. Follow existing code patterns in the codebase
2. For testing tasks: use TDD (write test first, then implement)
3. For refactor tasks: create interface, then implementation
4. For config tasks: verify environment and connectivity

### Phase 3: Verification
1. Run: mvn test
2. ALL tests must pass before proceeding
3. If tests fail, fix and retry (max 3 attempts)

### Phase 4: Documentation
1. Update progress.txt with your work
2. Set passes: true in task.json for completed task

### Phase 5: Commit
1. Stage: git add <files> (specific files only)
2. Commit with message: "Task #X: Description"

## Important Rules
- Complete only ONE task per session
- Tests MUST pass before marking complete
- If blocked after 3 attempts: add blocked:true to task, document in progress.txt
- Never delete tasks from task.json
- Commit code, progress.txt, and task.json together

## First Steps
1. Read CLAUDE.md for full instructions
2. Read task.json to find your task
3. Begin implementation

Stop when you have completed one task OR encountered an unresolvable blocker.
PROMPT_EOF
}

# Check if number argument is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <number_of_runs>"
    echo "Example: $0 5"
    exit 1
fi

# Validate input is a number
if ! [[ "$1" =~ ^[0-9]+$ ]]; then
    echo "Error: Argument must be a positive integer"
    exit 1
fi

TOTAL_RUNS=$1

# Banner
echo ""
echo "========================================"
echo "  Claude Code Automation Runner"
echo "  Stability Matrix Project"
echo "========================================"
echo ""

log "INFO" "Starting automation with $TOTAL_RUNS runs"
log "INFO" "Log file: $LOG_FILE"

# Check prerequisites
check_prerequisites

# Initial task count
INITIAL_TASKS=$(count_remaining_tasks)
BLOCKED_TASKS=$(count_blocked_tasks)
log "INFO" "Tasks remaining at start: $INITIAL_TASKS"
log "INFO" "Blocked tasks: $BLOCKED_TASKS"

if [ "$INITIAL_TASKS" -eq 0 ]; then
    log "SUCCESS" "All tasks already completed!"
    exit 0
fi

# Main loop
for ((run=1; run<=TOTAL_RUNS; run++)); do
    echo ""
    echo "========================================"
    log "PROGRESS" "Run $run of $TOTAL_RUNS"
    echo "========================================"

    # Check remaining tasks before this run
    REMAINING=$(count_remaining_tasks)

    if [ "$REMAINING" -eq 0 ]; then
        log "SUCCESS" "All tasks completed! No more tasks to process."
        log "INFO" "Automation finished early after $((run-1)) runs"
        exit 0
    fi

    # Get next task ID
    NEXT_TASK_ID=$(get_next_task_id)
    log "INFO" "Next task to process: #$NEXT_TASK_ID"
    log "INFO" "Tasks remaining before this run: $REMAINING"

    # Run timestamp for this iteration
    RUN_START=$(date +%s)
    RUN_LOG="$LOG_DIR/run-${run}-task${NEXT_TASK_ID}-$(date +%Y%m%d_%H%M%S).log"

    log "INFO" "Starting Claude Code session..."
    log "INFO" "Run log: $RUN_LOG"

    # Create prompt
    PROMPT_FILE=$(mktemp)
    create_prompt "$NEXT_TASK_ID" "$REMAINING" > "$PROMPT_FILE"

    # Run Claude with the prompt
    EXIT_CODE=0
    if claude -p \
        --dangerously-skip-permissions \
        --allowed-tools "Bash Edit Read Write Glob Grep Task WebSearch WebFetch" \
        < "$PROMPT_FILE" 2>&1 | tee "$RUN_LOG"; then

        RUN_END=$(date +%s)
        RUN_DURATION=$((RUN_END - RUN_START))

        log "SUCCESS" "Run $run completed in ${RUN_DURATION} seconds"
        CONSECUTIVE_FAILURES=0
    else
        EXIT_CODE=$?
        RUN_END=$(date +%s)
        RUN_DURATION=$((RUN_END - RUN_START))

        log "WARNING" "Run $run finished with exit code $EXIT_CODE after ${RUN_DURATION} seconds"
        CONSECUTIVE_FAILURES=$((CONSECUTIVE_FAILURES + 1))
    fi

    # Clean up temp file
    rm -f "$PROMPT_FILE"

    # Check remaining tasks after this run
    REMAINING_AFTER=$(count_remaining_tasks)
    COMPLETED=$((REMAINING - REMAINING_AFTER))

    if [ "$COMPLETED" -gt 0 ]; then
        log "SUCCESS" "Task(s) completed this run: $COMPLETED"
    else
        log "WARNING" "No tasks marked as completed this run"

        # Check if task was blocked
        NEW_BLOCKED=$(count_blocked_tasks)
        if [ "$NEW_BLOCKED" -gt "$BLOCKED_TASKS" ]; then
            log "WARNING" "Task was marked as blocked"
            BLOCKED_TASKS=$NEW_BLOCKED
        fi
    fi

    log "INFO" "Tasks remaining after run $run: $REMAINING_AFTER"

    # Check for consecutive failures
    if [ "$CONSECUTIVE_FAILURES" -ge "$MAX_CONSECUTIVE_FAILURES" ]; then
        log "ERROR" "Too many consecutive failures ($CONSECUTIVE_FAILURES). Stopping."
        exit 1
    fi

    # Add separator in log
    echo "" >> "$LOG_FILE"
    echo "----------------------------------------" >> "$LOG_FILE"
    echo "" >> "$LOG_FILE"

    # Delay between runs
    if [ $run -lt $TOTAL_RUNS ]; then
        log "INFO" "Waiting 3 seconds before next run..."
        sleep 3
    fi
done

# Final summary
echo ""
echo "========================================"
log "SUCCESS" "Automation completed!"
echo "========================================"

FINAL_REMAINING=$(count_remaining_tasks)
FINAL_BLOCKED=$(count_blocked_tasks)
TOTAL_COMPLETED=$((INITIAL_TASKS - FINAL_REMAINING))

log "INFO" "Summary:"
log "INFO" "  - Total runs: $TOTAL_RUNS"
log "INFO" "  - Tasks completed: $TOTAL_COMPLETED"
log "INFO" "  - Tasks remaining: $FINAL_REMAINING"
log "INFO" "  - Tasks blocked: $FINAL_BLOCKED"
log "INFO" "  - Log directory: $LOG_DIR"

if [ "$FINAL_REMAINING" -eq 0 ]; then
    log "SUCCESS" "All tasks have been completed!"
    exit 0
elif [ "$FINAL_BLOCKED" -gt 0 ]; then
    log "WARNING" "Some tasks are blocked. Check task.json and progress.txt for details."
    exit 0
else
    log "WARNING" "Some tasks remain. You may need to run more iterations."
    log "INFO" "Run: ./run-automation.sh <additional_runs>"
fi
