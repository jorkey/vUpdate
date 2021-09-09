import React, {useState} from "react";
import {LogLine, SequencedLogLine} from "../../../generated/graphql";
import GridTable from "../gridTable/GridTable";
import {makeStyles} from "@material-ui/core/styles";
import {GridTableColumnParams, GridTableColumnValue} from "../gridTable/GridTableColumn";
import {FindLogsDashboardParams} from "./LogsGetter";

const useStyles = makeStyles(theme => ({
  div: {
    display: 'relative'
  },
  logsTable: {
    // height: '300px'
    height: 'calc(100vh - 550px)',
  },
  timeColumn: {
    width: '200px',
    padding: '4px',
    paddingLeft: '16px'
  },
  levelColumn: {
    width: '100px',
    padding: '4px',
    paddingLeft: '16px'
  },
  messageColumn: {
    padding: '4px',
    paddingLeft: '16px'
  },
}))

interface LogsTableParams extends FindLogsDashboardParams {
  onComplete: (time: Date, status: boolean) => void
}

export const LogsTable = (props: LogsTableParams) => {
  const classes = useStyles()

  const [ lines, setLines ] = useState<SequencedLogLine[]>([])

  const columns: GridTableColumnParams[] = [
    {
      name: 'time',
      headerName: 'Time',
      className: classes.timeColumn,
      type: 'date',
    },
    {
      name: 'level',
      headerName: 'Level',
      className: classes.levelColumn
    },
    {
      name: 'message',
      headerName: 'Line',
      className: classes.messageColumn
    },
  ]

  const rows = lines
      .map(line => line.line)
      .map(line => new Map<string, GridTableColumnValue>([
    ['time', line.time],
    ['level', line.level],
    ['unit', line.unit],
    ['message', line.message]
  ]))

  return <GridTable
    className={classes.logsTable}
    columns={columns}
    rows={rows}
  />;
}
