interface QuestStepsProps<Key extends string> {
  steps: { key: Key; label: string }[]
  current: Key
}

/** The numbered step indicator at the top of a multi-step wizard. */
function QuestSteps<Key extends string>({ steps, current }: QuestStepsProps<Key>) {
  const currentIndex = steps.findIndex((step) => step.key === current)

  return (
    <div className="quest-steps">
      {steps.map((step, index) => {
        const isActive = index === currentIndex
        const isDone = index < currentIndex
        return (
          <div key={step.key} className={'quest-step' + (isActive ? ' is-active' : isDone ? ' is-done' : '')}>
            <div className="quest-step-medallion">{isDone ? '✓' : index + 1}</div>
            <span className="quest-step-label">{isActive ? step.label : ''}</span>
          </div>
        )
      })}
    </div>
  )
}

export default QuestSteps
