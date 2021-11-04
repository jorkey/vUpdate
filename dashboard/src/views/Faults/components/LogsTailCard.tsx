import React from "react";
import {
  LogLine,
} from "../../../generated/graphql";
import {makeStyles} from "@material-ui/core/styles";
import {GridTableColumnParams, GridTableColumnValue} from "../../../common/components/gridTable/GridTableColumn";
import GridTable from "../../../common/components/gridTable/GridTable";
import {Card, CardContent, CardHeader} from "@material-ui/core";

const useStyles = makeStyles(theme => ({
  content: {
    padding: 0
  },
  inner: {
    minWidth: 800
  },
  logsTable: {
    height: '250px'
  },
  timeColumn: {
    width: '200px',
    minWidth: '200px',
    padding: '4px',
    paddingLeft: '16px'
  },
  levelColumn: {
    width: '100px',
    minWidth: '100px',
    padding: '4px',
    paddingLeft: '16px'
  },
  messageColumn: {
    whiteSpace: 'pre',
    padding: '4px',
    paddingLeft: '16px'
  },
}))

interface LogsTailCardParams {
  lines: LogLine[]
}

export const LogsTailCard = (props: LogsTailCardParams) => {
  const { lines } = props

  const classes = useStyles()

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
    .map(line => {
      return new Map<string, GridTableColumnValue>([
        ['time', line.time],
        ['level', line.level],
        ['unit', line.unit],
        ['message', line.message]
      ]) })

  return <Card>
    <CardHeader title={'Logs Tail'}/>
    <CardContent className={classes.content}>
      <div className={classes.inner}>
        <GridTable
          className={classes.logsTable}
          columns={columns}
          rows={rows}
          scrollToLastRow={true}
        />
      </div>
    </CardContent>
  </Card>
}